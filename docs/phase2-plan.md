# Phase 2: AI-Powered Fraud Analysis — Implementation Plan

Layer RAG/LLM intelligence onto the existing event-driven pipeline. Five PRs, each on its own feature branch, ordered by dependency.

## Current State

- REST API → Kafka → Consumer → PostgreSQL pipeline working E2E
- Hexagonal architecture enforced by ArchUnit (5 rules)
- pgvector already running in Docker Compose (`pgvector/pgvector:pg16`, `wal_level=logical`)
- Virtual Threads enabled, CompletableFuture scatter-gather pattern documented in ADR-0002
- 33 tests across 7 test classes
- `EventPublisherPort.publishTransactionReceived(Transaction)` writes directly to Kafka (dual-write risk)
- `TransactionReceivedEvent` is a package-private record in the messaging package — no domain-level event hierarchy

---

## PR #3 — Sealed Event Interfaces

**Branch:** `feature/sealed-events`
**Goal:** Establish a domain-level event type hierarchy with exhaustive pattern matching.

### Domain model changes

New `domain.event` package with sealed interface:

```java
public sealed interface TransactionEvent
    permits TransactionReceived, TransactionEvaluated {
    TransactionId transactionId();
    Instant occurredAt();
}
```

- `TransactionReceived` record — wraps the full `Transaction` domain object
- `TransactionEvaluated` record — carries `Transaction`, `FraudDecision` (decision enum + risk score + reasoning)
- `FraudDecision` record in `domain.model`: `decision` (enum: APPROVE, FLAG, BLOCK), `riskScore` (BigDecimal 0-100), `reasoning` (String), `evaluatedAt` (Instant)

### Port changes

- `EventPublisherPort.publish(TransactionEvent)` replaces `publishTransactionReceived(Transaction)`
- `EvaluateTransactionUseCaseImpl.submit()` wraps the transaction in `TransactionReceived` before publishing

### Infrastructure changes

- `EventPublisherKafkaAdapter` updated to serialize `TransactionEvent` with a `type` discriminator field in the JSON payload (Jackson 3.x `tools.jackson.annotation.JsonTypeInfo` on the sealed interface for polymorphic deserialization — note this project uses Jackson 3.x, not `com.fasterxml.jackson`)
- Consumer updated to deserialize based on `type` discriminator and switch exhaustively on sealed type:
  - `TransactionReceived` → persist to DB (existing flow)
  - `TransactionEvaluated` → log/audit the fraud decision (no-op in PR #3, wired in PR #5)
- Existing infrastructure `TransactionReceivedEvent` DTO in messaging package is retained as the Kafka deserialization target; it maps to the domain `TransactionReceived` event inside the consumer

### Tests

- Unit tests for sealed event construction and pattern matching
- Update existing Kafka integration test for new event format
- ArchUnit: allow `domain.event` in domain dependency rules

---

## PR #4 — LangChain4j RAG with pgvector

**Branch:** `feature/rag-pgvector`
**Goal:** Store transaction embeddings and retrieve similar historical transactions.

### Dependencies (build.gradle.kts)

```
dev.langchain4j:langchain4j:1.11.0
dev.langchain4j:langchain4j-pgvector:1.11.0-beta19
dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2:1.11.0-beta19
dev.langchain4j:langchain4j-open-ai:1.11.0-beta19  // optional, for production LLM
```

Note: Use LangChain4j core directly (no Spring Boot starter) to avoid Spring Boot 4.0 compatibility issues. Wire beans manually — the hexagonal architecture makes this natural.

### Database

Flyway `V2__create_embeddings_table.sql`:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE transaction_embeddings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID NOT NULL REFERENCES transactions(id),
    embedding       vector(384) NOT NULL,  -- all-MiniLM-L6-v2 produces 384 dimensions
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_embeddings_transaction_id ON transaction_embeddings(transaction_id);
CREATE INDEX idx_embeddings_vector ON transaction_embeddings USING hnsw (embedding vector_cosine_ops);
```

### Domain ports

- New outbound port `EmbeddingStorePort`:
  - `store(TransactionId, Transaction)` — generates embedding from transaction text and stores it
  - `findSimilar(Transaction, int maxResults)` — returns `List<ScoredTransaction>` (transaction + similarity score)
- New `ScoredTransaction` record in `domain.model` (wraps `Transaction` + `double similarityScore`)
- The adapter stores the `TransactionId` as metadata alongside the embedding vector; `findSimilar` queries pgvector for nearest neighbors, extracts transaction IDs from metadata, then loads full `Transaction` objects via `TransactionRepository`

### Infrastructure adapter

- `PgVectorEmbeddingAdapter` implements `EmbeddingStorePort` using LangChain4j's `PgVectorEmbeddingStore` and `AllMiniLmL6V2EmbeddingModel`
- Transaction text representation: `"{amount} {currency} from {fromAccount} to {toAccount}: {description}"`
- Configuration bean class wiring the embedding model and store (no Spring starter needed)

### Embedding ingestion

Embeddings are generated and stored at transaction ingestion time — when the Kafka consumer persists a transaction, it also calls `embeddingStorePort.store(transactionId, transaction)`. This ensures every persisted transaction has a corresponding embedding for similarity search. No separate batch pipeline is needed for Phase 2; document-based RAG (policy manuals, rule descriptions) is deferred to a future phase.

### Tests

- Integration test with Testcontainers PostgreSQL: store embedding, retrieve similar
- Unit test for text representation generation

---

## PR #5 — Two-Phase Fraud Evaluation

**Branch:** `feature/fraud-evaluation`
**Goal:** Rule gate → parallel scatter-gather for flagged transactions.

### Domain model

- `RuleGateResult` record: `decision` (PASS, BLOCK), `reason` (String)
- Outbound port `UserHistoryPort`: `findRecentByAccount(AccountId, int limit)` → `List<Transaction>`
  - Adapter: `UserHistoryAdapter` queries the existing `transactions` table via `TransactionJpaRepository` with a custom query (`findByFromAccountOrToAccountOrderByOccurredAtDesc`)
- Outbound port `LlmFraudAnalysisPort`: `analyze(RedactedTransaction, List<ScoredTransaction>)` → `LlmFraudAssessment` (risk score + reasoning text)
  - Adapter: wraps LangChain4j `ChatLanguageModel` (Ollama for local dev, OpenAI for production). Mocked in tests.

### Application layer

- New inbound port `FraudEvaluationUseCase` with `evaluate(Transaction)` → `FraudDecision`
- `FraudEvaluationUseCaseImpl` orchestrates:
  - **Phase 1 — Rule Gate** (<5ms): synchronous domain service `RuleGateService` checks amount threshold (>$50,000 → FLAG), velocity (via UserHistoryPort), sanctioned-pattern matching. Returns BLOCK for obvious fraud, APPROVE for clearly safe, FLAG for uncertain.
  - **Phase 2 — Scatter-Gather** (flagged only): three parallel CompletableFuture tasks on Virtual Thread executor:
    1. RAG similarity search via `EmbeddingStorePort.findSimilar()`
    2. User history analysis via `UserHistoryPort.findRecentByAccount()`
    3. LLM fraud analysis via `LlmFraudAnalysisPort.analyze()` (receives PII-redacted transaction + RAG context)
  - Results aggregated with weighted scoring into final `FraudDecision`
  - `orTimeout(5, SECONDS)` on each future for bounded latency
  - Note: the LLM task receives PII-redacted data (PII redaction from PR #7 is prerequisite; until then, LLM receives raw data or is mocked)

### Consumer integration

- `TransactionKafkaConsumer` updated: after persist, calls `fraudEvaluationUseCase.evaluate(transaction)`, then publishes `TransactionEvaluated` event with the decision

### Tests

- Unit tests for rule gate logic (threshold, velocity, sanctioned patterns)
- Unit test for scatter-gather aggregation with mocked ports
- Integration test: full flow from Kafka message → persist → evaluate → result event
- Property-based: random transactions produce valid FraudDecision (score in 0-100, non-null reasoning)

---

## PR #6 — Transactional Outbox + Debezium CDC

**Branch:** `feature/outbox-cdc`
**Goal:** Replace direct Kafka publishing with outbox writes for consistency.

### Database

Flyway `V3__create_outbox_table.sql`:

```sql
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(255) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Infrastructure changes

- New `OutboxEventPublisherAdapter` implements `EventPublisherPort`
  - Instead of sending to Kafka, INSERTs into `outbox_events` within the same `@Transactional` boundary
  - `aggregate_type` = `"Transaction"`, `aggregate_id` = transaction ID, `event_type` = sealed type name
- Remove direct Kafka dependency from `EventPublisherPort` implementations
- New JPA entity `OutboxEventEntity` + `OutboxJpaRepository`

### Docker Compose additions

- Add Debezium Kafka Connect service (`debezium/connect:2.5`) to `docker/docker-compose.yml`
- Debezium connector config JSON at `docker/debezium-connector.json`:
  - PostgreSQL connector watching `public.outbox_events`
  - `outbox.EventRouter` SMT routing by `aggregate_type`
  - Topic pattern: `transactions.events`

### Consumer updates

- Consumer topic changes from `transactions.incoming` to `transactions.events` (or reads both during migration)
- Deserialize outbox event payload format

### Tests

- Integration test: write to outbox → verify row exists in outbox_events table
- E2E test with Testcontainers (PostgreSQL + Kafka + Debezium Connect containers) to verify outbox row is captured by CDC and published to Kafka topic

### ADR

- `ADR-0004: Transactional Outbox with Debezium CDC`

---

## PR #7 — PII Redaction + Resilience4j

**Branch:** `feature/pii-resilience`
**Goal:** Zero-trust PII handling and graceful LLM degradation.

### PII Redaction (domain concern)

- New `domain.service.PiiRedactor` class:
  - `redact(Transaction)` → `RedactedTransaction` record
  - Account IDs → SHA-256 hash prefix (irreversible)
  - Description → regex-based PII scrub (email, phone, SSN patterns)
  - Amount and currency preserved (needed for risk scoring)
- `FraudEvaluationUseCaseImpl` calls `piiRedactor.redact()` before passing to RAG/LLM evaluation
- This is a domain concern, not infrastructure — PII never reaching the LLM is a business invariant

### Resilience4j

- Dependencies: `io.github.resilience4j:resilience4j-all:2.3.0` (use core library directly if Spring Boot 4.0 starter isn't compatible)
- Circuit breaker wrapping the LLM adapter call in the scatter-gather phase
- Config in `application.yml`: 50% failure rate threshold, 60s wait in open state, 10-call sliding window
- Fallback: when LLM is unavailable, return rule-based-only decision with `reasoning: "LLM unavailable — rule-based fallback"`

### Tests

- Unit tests for PII redaction (account ID hashing, description scrubbing)
- Property-based: redacted output never contains original account IDs
- Unit test: circuit breaker open → fallback decision returned
- Integration test: LLM timeout → circuit opens → subsequent calls use fallback

### ADR

- `ADR-0005: Irreversible PII Redaction Before LLM Processing`

---

## Technical Risks and Mitigations

**LangChain4j + Spring Boot 4.0 compatibility:** The langchain4j-spring-boot-starter may not support Spring Boot 4.0 yet. Mitigation: use langchain4j core directly, wire beans manually behind hexagonal port interfaces.

**Jackson version conflict:** This project uses Jackson 3.x (`tools.jackson.*`), but LangChain4j likely bundles Jackson 2.x (`com.fasterxml.jackson.*`) internally. Both can coexist on the classpath since they use different package namespaces. However, any LangChain4j types that need serialization by our code must go through our Jackson 3.x ObjectMapper, not LangChain4j's internal one. The hexagonal port boundary naturally enforces this — domain objects never leak LangChain4j types.

**Resilience4j + Spring Boot 4.0:** Same risk. Mitigation: use resilience4j-all core library with programmatic configuration.

**In-process embedding model:** all-MiniLM-L6-v2 produces 384-dim vectors (not 1536 like OpenAI). This is fine for our scale and keeps CI API-key-free. Document dimension choice in migration.

**PII redaction is irreversible by design:** SHA-256 hashing means original account IDs are unrecoverable from the LLM audit path. This is intentional — fraud analysts access original PII through the main transaction table, not through the LLM context. If reversible masking is ever needed, the `PiiRedactor` interface allows swapping to an encryption-based implementation behind the same port.

**LLM API key in CI:** Mock the LLM chat model in tests. For local dev, Ollama provides a zero-cost local option.

**Debezium in CI:** Testcontainers has a Debezium module, but it adds significant test time. Keep Debezium integration tests in a separate Gradle task or test tag.

---

## Estimated Effort

- PR #3 (Sealed events): 1-2 hours
- PR #4 (RAG + pgvector): 4-6 hours
- PR #5 (Fraud evaluation): 4-6 hours
- PR #6 (Outbox + Debezium): 3-4 hours
- PR #7 (PII + Resilience4j): 3-4 hours
- **Total: ~2-3 weekends**
