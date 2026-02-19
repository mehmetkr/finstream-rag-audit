# FinStream-RAG-Audit

> Real-time financial transaction fraud detection — high-throughput event processing with an architecture designed for AI-powered (RAG/LLM) audit analysis.

![CI](https://github.com/mehmetkr/finstream-rag-audit/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green)
![Kafka](https://img.shields.io/badge/Kafka-Event--Driven-blue)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)
![Tests](https://img.shields.io/badge/Tests-118%20passing-brightgreen)
![Architecture](https://img.shields.io/badge/Architecture-Hexagonal-purple)

---

## Why This Project Exists

Real-time fraud detection requires sub-50ms latency. Introducing LLMs (RAG) typically adds 500ms+. This project demonstrates how to decouple high-throughput transaction processing from AI-based audit analysis using event-driven architecture, Virtual Threads, and Scoped Values — built on Java 25 and Spring Boot 4.0.

The system is being developed in iterative phases. **Phase 1 (core pipeline)** and **Phase 2 (AI-powered fraud analysis)** are complete and fully tested. Phase 3 added OAuth2 security and distributed tracing (OpenTelemetry).

---

## Architecture

Hexagonal (Ports & Adapters) architecture with an event-driven core. Domain logic is completely isolated from infrastructure concerns.

```
                              ┌─────────────────────────────────────────────┐
                              │          FinStream-RAG-Audit                │
                              ├─────────────────────────────────────────────┤
                              │                                             │
  ┌──────────────┐            │  ┌──────────────────────────────────────┐   │
  │              │   POST     │  │          Transaction Controller      │   │
  │    Client    │──────────► │  │          (REST API)                  │   │
  │              │            │  └──────────────┬───────────────────────┘   │
  │              │            │                 │                           │
  └──────────────┘            │                 ▼                           │
                              │  ┌──────────────────────────────────────┐   │
                              │  │      EvaluateTransactionUseCase      │   │
                              │  │      (Application Layer)             │   │
                              │  └──────┬────────────┬──────────┬───────┘   │
                              │         │            │          │           │
                              │         ▼            ▼          ▼           │
                              │  ┌────────────┐ ┌──────────┐ ┌────────────┐ │
                              │  │ Rule Gate  │ │ RAG Search││ User History│ │
                              │  │ Service    │ │ (Vector) │ │ Port       │ │
                              │  └──────┬─────┘ └────┬─────┘ └──────┬─────┘ │
                              │         │            │              │       │
                              │         ▼            ▼              ▼       │
                              │  ┌──────────────────────────────────────┐   │
                              │  │       LLM Fraud Analysis Port        │   │
                              │  │       (Resilience4j + PII Redaction) │   │
                              │  └───────────────────┬──────────────────┘   │
                              │                      │                      │
                              │                      ▼                      │
                              │  ┌──────────────────────────────────────┐   │
                              │  │       OutboxEventPublisherAdapter    │   │
                              │  │       (Transactional Outbox)         │   │
                              │  └───────────────────┬──────────────────┘   │
                              │                      │                      │
                              │                      ▼                      │
                              │            ┌──────────────────┐             │
                              │            │   PostgreSQL     │             │
                              │            │ (Table + Outbox) │             │
                              │            └─────────┬────────┘             │
                              │                      │ CDC (Debezium)       │
                              │                      ▼                      │
                              │            ┌──────────────────┐             │
                              │            │      Kafka       │             │
                              │            │   (Event Bus)    │             │
                              │            └──────────────────┘             │
                              └─────────────────────────────────────────────┘
```
---

## Current Status — Phase 3 In Progress (Security Complete)

The system is fully operational with an event-driven core, AI-powered fraud analysis, and production-grade security.

- **Security** — OAuth 2.0 Resource Server with Keycloak (JWT authentication)
- **Two-Phase Fraud Evaluation** — Fast rule gate (<5ms) followed by parallel RAG/LLM analysis
- **LangChain4j RAG** — pgvector similarity search retrieves historical context
- **Transactional Outbox + CDC** — Debezium eliminates dual-write inconsistencies
- **PII Redaction** — Irreversible SHA-256 hashing + regex scrubbing
- **Resilience** — Circuit breakers (Resilience4j)
- **118 tests** — Architecture, integration, property-based, and security tests

See [`docs/ROADMAP.md`](docs/ROADMAP.md) for remaining Phase 3 plans (Observability, Performance).

---

## Fraud Evaluation Flow

1. **Ingestion**: Transaction is received via REST API and persisted to PostgreSQL.
2. **Phase 1 (Sync)**: `RuleGateService` checks amount thresholds and velocity.
   - **BLOCK**: Immediate rejection (e.g., sanctioned entity).
   - **APPROVE**: Immediate approval (low risk).
   - **FLAG**: Proceed to Phase 2.
3. **Phase 2 (Async)**: If flagged, the system fans out parallel tasks using Virtual Threads:
   - **RAG Search**: Finds historically similar transactions (pgvector).
   - **User History**: Fetches recent activity for the account.
   - **LLM Analysis**: Combines RAG context + History + PII-redacted transaction data to prompt the LLM for a risk score.
4. **Decision**: Scores are aggregated (weighted average) to produce a final `APPROVE` or `BLOCK` decision.
5. **Audit**: The decision and reasoning are published to Kafka via the Transactional Outbox pattern.

---

## Tech Stack

**Language & Framework:** Java 25, Spring Boot 4.0.2, Spring Security 6, Gradle 9.2 (Kotlin DSL)

**AI & Data:** LangChain4j (RAG), PostgreSQL 16 + pgvector, Debezium (CDC)

**Infrastructure:** Apache Kafka, Keycloak (Identity Provider), Flyway, Resilience4j

**Testing:** JUnit 5, Testcontainers 2.0, ArchUnit 1.4.1, jqwik 1.9.2, Awaitility

**Architecture:** Hexagonal (Ports & Adapters), Event-Driven, Outbox Pattern

---

## Java 25 Features in Use

### Virtual Threads

Every HTTP request, Kafka consumer, and async task runs on lightweight virtual threads — no thread pool tuning required.

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

### Scoped Values

Immutable, thread-safe request context propagation — replaces `ThreadLocal` for virtual thread workloads.

```java
public class RequestContext {
    public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();
}
```

### Records & Strongly-Typed IDs

Immutable domain models with compile-time validation — prevents primitive obsession and parameter-swapping bugs.

```java
public record TransactionId(UUID value) {
    public TransactionId {
        Objects.requireNonNull(value, "TransactionId cannot be null");
    }
    public static TransactionId generate() {
        return new TransactionId(UUID.randomUUID());
    }
}

public record AccountId(String value) {
    public AccountId {
        if (value == null || !value.matches("^[A-Z]{2}\\d{10}$")) {
            throw new IllegalArgumentException("Invalid account ID format: " + value);
        }
    }
}
```

---

## Quick Start

### Prerequisites

- JDK 25+
- Docker & Docker Compose

### Run

```bash
# 1. Clone
git clone https://github.com/mehmetkr/finstream-rag-audit.git
cd finstream-rag-audit

# 2. Start infrastructure (Kafka + PostgreSQL)
docker compose -f docker/docker-compose.yml up -d

# 3. Wait for services to be healthy, then start the app
./gradlew bootRun

# 4. Obtain a JWT token from Keycloak
TOKEN=$(curl -s -X POST http://localhost:8081/realms/finstream/protocol/openid-connect/token \
  -d "grant_type=client_credentials" \
  -d "client_id=finstream-api" \
  -d "client_secret=secret" | jq -r '.access_token')

# 5. Submit a high-value transaction (triggers fraud evaluation)
curl -X POST http://localhost:8080/api/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 15000.00,
    "currency": "USD",
    "fromAccount": "US1234567890",
    "toAccount": "GB9876543210",
    "description": "Large international wire transfer"
  }'
# Returns: 202 Accepted

# 6. Check logs for fraud evaluation result
# (In the terminal where ./gradlew bootRun is running)
# You should see: "Aggregated score for <uuid>: <score> → FLAG/BLOCK"
# along with reasoning components (Rule gate | LLM | History)

# 7. Verify Outbox event persistence (optional)
docker exec docker-postgres-1 psql -U finstream -d finstream \
  -c "SELECT event_type, payload->'riskScore' as score FROM outbox_events WHERE event_type = 'TransactionEvaluated';"

# 8. Cleanup
docker compose -f docker/docker-compose.yml down
```

### Run Tests

```bash
./gradlew check
```

All 118 tests pass, covering:
- **Security** — OAuth2 integration, 401/403 handling
- **Architecture** — ArchUnit enforces hexagonal boundaries
- **Integration** — Testcontainers with real Kafka and PostgreSQL (Outbox, RAG, Debezium)
- **Property-based** — jqwik generates random inputs to test domain invariants (PII redaction safety)
- **Unit** — Domain model validation, use case logic (Scatter-gather, Circuit Breaker, Rule Gate)

---

## Project Structure

```
src/
├── main/java/com/finstream/
│   ├── FinStreamApplication.java
│   │
│   ├── domain/                         # Pure business logic — no framework dependencies
│   │   ├── event/                      # Sealed event hierarchy
│   │   │   ├── TransactionEvent.java
│   │   │   ├── TransactionReceived.java
│   │   │   └── TransactionEvaluated.java
│   │   ├── model/
│   │   │   ├── Transaction.java        # Core domain record
│   │   │   ├── FraudDecision.java      # Evaluation result
│   │   │   ├── RedactedTransaction.java # PII-safe projection
│   │   │   └── ...
│   │   ├── ports/
│   │   │   ├── inbound/
│   │   │   │   └── FraudEvaluationUseCase.java
│   │   │   └── outbound/
│   │   │       ├── EmbeddingStorePort.java
│   │   │       ├── LlmFraudAnalysisPort.java
│   │   │       ├── UserHistoryPort.java
│   │   │       └── ...
│   │   └── service/
│   │       ├── PiiRedactor.java        # Domain service (SHA-256 + Regex)
│   │       └── RuleGateService.java    # Phase 1 logic
│   │
│   ├── application/                    # Use case orchestration
│   │   └── FraudEvaluationUseCaseImpl.java # Scatter-gather logic
│   │
│   └── infrastructure/                 # Adapters — framework-dependent code
│       ├── config/
│       │   ├── FraudEvaluationConfiguration.java
│       │   ├── FraudEvaluationProperties.java
│       │   └── SecurityConfiguration.java
│       └── adapters/
│           ├── web/
│           │   └── TransactionController.java
│           ├── messaging/
│           │   └── OutboxEventPublisherAdapter.java
│           ├── persistence/
│           │   └── TransactionJpaRepository.java
│           ├── embedding/
│           │   └── PgVectorEmbeddingAdapter.java
│           └── llm/
│               ├── StubLlmFraudAnalysisAdapter.java
│               └── ResilientLlmFraudAnalysisAdapter.java # Circuit Breaker decorator
```

---

## Architecture Decision Records

Key architectural decisions are documented in [`docs/adr/`](docs/adr/):

- **[ADR-0001](docs/adr/0001-hexagonal-architecture.md):** Hexagonal architecture — domain isolation, framework-independent testability
- **[ADR-0002](docs/adr/0002-completablefuture-virtual-threads.md):** CompletableFuture + Virtual Threads over StructuredTaskScope — production-stable APIs, two-phase gating
- **[ADR-0003](docs/adr/0003-pgvector-over-pinecone.md):** pgvector over Pinecone for vector storage — ACID co-location, no SaaS dependency
- **[ADR-0004](docs/adr/0004-transactional-outbox-debezium.md):** Transactional Outbox with Debezium CDC — eliminates dual-write inconsistency
- **[ADR-0005](docs/adr/0005-pii-redaction-before-llm.md):** Irreversible PII Redaction Before LLM Processing — SHA-256 hashing and regex scrubbing

---

## License

MIT
