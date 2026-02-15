# FinStream-RAG-Audit

> Real-time financial transaction fraud detection — high-throughput event processing with an architecture designed for AI-powered (RAG/LLM) audit analysis.

![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green)
![Kafka](https://img.shields.io/badge/Kafka-Event--Driven-blue)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)
![Tests](https://img.shields.io/badge/Tests-33%20passing-brightgreen)
![Architecture](https://img.shields.io/badge/Architecture-Hexagonal-purple)

---

## Why This Project Exists

Real-time fraud detection requires sub-50ms latency. Introducing LLMs (RAG) typically adds 500ms+. This project demonstrates how to decouple high-throughput transaction processing from AI-based audit analysis using event-driven architecture, Virtual Threads, and Scoped Values — built on Java 25 and Spring Boot 4.0.

The system is being developed in iterative phases. **Phase 1 (core pipeline)** is complete and fully tested. Future phases will layer in RAG/LLM intelligence, the Transactional Outbox pattern, security, and observability on top of this foundation.

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
  └──────────────┘            │                 │                           │
                              │                 ▼                           │
                              │  ┌──────────────────────────────────────┐   │
                              │  │      EvaluateTransactionUseCase      │   │
                              │  │      (Application Layer)             │   │
                              │  └──────────┬───────────┬───────────────┘   │
                              │             │           │                   │
                              │             ▼           ▼                   │
                              │  ┌────────────────┐ ┌─────────────────┐    │
                              │  │ EventPublisher │ │ Transaction     │    │
                              │  │ Port (Kafka)   │ │ Repository Port │    │
                              │  └───────┬────────┘ └────────┬────────┘    │
                              │          │                   │             │
                              └──────────┼───────────────────┼─────────────┘
                                         │                   │
                                         ▼                   ▼
                              ┌────────────────┐  ┌────────────────┐
                              │     Kafka      │  │   PostgreSQL   │
                              │  (Event Bus)   │  │   (pgvector)   │
                              └───────┬────────┘  └────────────────┘
                                      │                   ▲
                                      ▼                   │
                              ┌────────────────────────────┘
                              │  Transaction Kafka Consumer
                              │  (Persists to DB)
                              └────────────────────────────
```

### Target Architecture (Full Vision)

```
  Client ──► API Gateway (JWT, Rate Limiting, PII Redaction)
                  │
                  ▼
          Transaction Ingestion ──► Kafka ──► Fraud Evaluation Service
                                                │
                                    ┌───────────┼───────────┐
                                    ▼           ▼           ▼
                              Rule Engine   RAG Search   User History
                              (<5ms gate)   (pgvector)   (Postgres)
                                    │           │           │
                                    └───────────┼───────────┘
                                                ▼
                                    LLM Decision (LangChain4j)
                                    APPROVE | FLAG | BLOCK
                                                │
                              ┌─────────────────┼─────────────────┐
                              ▼                 ▼                 ▼
                          PostgreSQL      Audit Log (Kafka)   Alerts
                          (Outbox + CDC)
```

---

## Current Status

### ✅ Phase 1: Core Event-Driven Pipeline — Complete

The foundational high-throughput transaction ingestion and processing system, fully tested and working end-to-end.

**What's built:**

- **REST API** — Transaction submission endpoint with Bean Validation
- **Event-driven pipeline** — Kafka producer/consumer for async processing
- **PostgreSQL persistence** — Flyway-managed schema, JPA repositories
- **Hexagonal architecture** — Clean separation of domain, application, and infrastructure layers
- **Comprehensive test suite** — 33 tests covering architecture, integration, property-based, and unit tests
- **Virtual Threads** — Enabled platform-wide for lightweight concurrency
- **Scoped Values** — Request context propagation without ThreadLocal

### ➡️ Phase 2: AI-Powered Fraud Analysis

LangChain4j RAG with pgvector, two-phase fraud evaluation (fast rule gate → CompletableFuture scatter-gather), Transactional Outbox with Debezium CDC, PII redaction, Resilience4j fallback.

### ➡️ Phase 3: Production Hardening

OAuth 2.0 / Keycloak, OpenTelemetry tracing, Grafana dashboards, JMH benchmarks.

---

## Tech Stack

**Language & Framework:** Java 25, Spring Boot 4.0.2, Gradle 9.2 (Kotlin DSL)

**Infrastructure:** Apache Kafka (event streaming), PostgreSQL 16 + pgvector (persistence & vectors), Flyway (migrations)

**Testing:** JUnit 5, Testcontainers 2.0, ArchUnit 1.4.1, jqwik 1.9.2, Awaitility

**Architecture:** Hexagonal (Ports & Adapters), Event-Driven

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

# 4. Submit a test transaction
curl -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 1500.00,
    "currency": "USD",
    "fromAccount": "US1234567890",
    "toAccount": "GB9876543210",
    "description": "Wire transfer"
  }'
# Returns: 202 Accepted

# 5. Verify persistence
docker exec docker-postgres-1 psql -U finstream -d finstream \
  -c "SELECT id, amount, currency, from_account, to_account FROM transactions;"

# 6. Cleanup
docker compose -f docker/docker-compose.yml down
```

### Run Tests

```bash
./gradlew check
```

All 33 tests pass, covering:
- **Architecture** — ArchUnit enforces hexagonal boundaries (domain cannot depend on infrastructure or Spring)
- **Integration** — Testcontainers with real Kafka and PostgreSQL
- **Property-based** — jqwik generates random inputs to test domain invariants
- **Unit** — Domain model validation, use case logic

---

## Project Structure

```
src/
├── main/java/com/finstream/
│   ├── FinStreamApplication.java
│   │
│   ├── domain/                         # Pure business logic — no framework dependencies
│   │   ├── model/
│   │   │   ├── Transaction.java        # Core domain record
│   │   │   ├── Amount.java             # Value object with currency
│   │   │   ├── RequestContext.java      # ScopedValue-based context
│   │   │   └── ids/                    # Strongly-typed identifiers
│   │   │       ├── TransactionId.java
│   │   │       └── AccountId.java
│   │   └── ports/
│   │       ├── inbound/
│   │       │   └── EvaluateTransactionUseCase.java
│   │       └── outbound/
│   │           ├── EventPublisherPort.java
│   │           └── TransactionRepository.java
│   │
│   ├── application/                    # Use case orchestration
│   │   ├── usecase/
│   │   │   └── EvaluateTransactionUseCaseImpl.java
│   │   └── dto/
│   │       └── TransactionRequest.java
│   │
│   └── infrastructure/                 # Adapters — framework-dependent code
│       └── adapters/
│           ├── web/
│           │   └── TransactionController.java
│           ├── messaging/
│           │   ├── EventPublisherKafkaAdapter.java
│           │   └── TransactionKafkaConsumer.java
│           └── persistence/
│               ├── TransactionJpaRepository.java
│               └── entity/
│                   └── TransactionEntity.java
│
├── main/resources/
│   ├── application.yml
│   └── db/migration/
│       └── V1__create_transactions_table.sql
│
└── test/java/com/finstream/
    ├── architecture/
    │   └── ArchitectureTest.java       # Hexagonal boundary enforcement
    ├── domain/model/
    │   ├── DomainModelTest.java        # Domain invariant tests + jqwik
    │   └── RequestContextTest.java     # ScopedValue tests
    └── infrastructure/adapters/
        ├── messaging/
        │   └── KafkaIntegrationTest.java
        ├── persistence/
        │   └── TransactionRepositoryIntegrationTest.java
        └── web/
            └── TransactionControllerTest.java
```

---

## Architecture Decision Records

Key architectural decisions are documented in [`docs/adr/`](docs/adr/):

- **[ADR-0001](docs/adr/0001-hexagonal-architecture.md):** Hexagonal architecture — domain isolation, framework-independent testability
- **[ADR-0002](docs/adr/0002-completablefuture-virtual-threads.md):** CompletableFuture + Virtual Threads over StructuredTaskScope — production-stable APIs, two-phase gating
- **ADR-0003 (planned):** Use pgvector over Pinecone for vector storage
- **ADR-0004 (planned):** Irreversible PII redaction before LLM processing
- **ADR-0005 (planned):** Transactional Outbox with Debezium CDC

---

## License

MIT
