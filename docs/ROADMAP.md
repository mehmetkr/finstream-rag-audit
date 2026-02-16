# FinStream-RAG-Audit — Roadmap

This document outlines the planned evolution of the system beyond Phase 1.

---

## Target Architecture

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

## Phase 2: AI-Powered Fraud Analysis (Complete)

**Goal:** Layer RAG/LLM intelligence onto the existing event-driven pipeline.

- [x] **LangChain4j RAG integration** — pgvector similarity search retrieves historically similar transactions as LLM context
- [x] **Two-phase fraud evaluation** — fast rule gate (<5ms) screens obvious cases; flagged transactions fan out via CompletableFuture scatter-gather with Virtual Threads to rule engine, RAG search, and user history in parallel
- [x] **Sealed interfaces for event types** — exhaustive pattern matching for transaction lifecycle events
- [x] **Transactional Outbox with Debezium CDC** — eliminates dual-write inconsistency between PostgreSQL and Kafka
- [x] **PII redaction** — irreversible redaction before LLM processing; zero-trust, no token vault
- [x] **Resilience4j circuit breaker** — rule-based fallback when LLM service is unavailable

---

## Phase 3: Production Hardening (Active)

**Goal:** Security, observability, and performance validation for production readiness.

- **OAuth 2.0 / Keycloak** — authentication and authorization
- **OpenTelemetry** — distributed tracing across HTTP → Kafka → consumer → DB
- **Grafana dashboards** — transaction throughput, fraud detection latency, error rates
- **JMH benchmarks** — quantify throughput and latency under load

**Estimated effort:** 2 weekends
