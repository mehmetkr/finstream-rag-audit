# FinStream-RAG-Audit — Unmet Staff-Level Expectations

**Repository:** https://github.com/mehmetkr/finstream-rag-audit (v4.0.0, commit c0fba50)

---

## 1. Clock Injection for Testability

**Status:** Not implemented

**Current state:** `Instant.now()` is called directly in 12+ locations across 7 production classes:

- `FraudEvaluationUseCaseImpl` — 3 calls (velocity window calculation, FraudDecision timestamps)
- `EvaluateTransactionUseCaseImpl` — 1 call (TransactionReceived event timestamp)
- `TransactionKafkaConsumer` — 3 calls (TransactionEvaluated and error event timestamps)
- `OutboxEventPublisherAdapter` — 1 call (outbox entity createdAt)
- `OutboxCleanupScheduler` — 1 call (retention cutoff calculation)
- `GlobalExceptionHandler` — 3 calls (error response timestamps)
- `TransactionController` — 1 call (transaction occurredAt default)

**Why it matters:** Without `Clock` injection, time-dependent behavior cannot be tested deterministically. For example, velocity window calculations in `FraudEvaluationUseCaseImpl` depend on "now" — tests can't freeze or advance time to verify boundary conditions. Interviewers familiar with domain-driven design expect `Clock` as a constructor dependency in any service that reasons about time.

**Estimated effort:** 20 minutes

**Fix:** Inject `java.time.Clock` into each affected class via constructor parameter. Use `Clock.systemUTC()` in production config and `Clock.fixed()` in tests.

---

## 2. Distributed Tracing (OpenTelemetry)

**Status:** Not implemented — deferred to Phase 3 in ROADMAP.md

**Current state:** Basic observability exists via Spring Boot Actuator (`health`, `info`, `metrics` endpoints exposed and security-whitelisted). However, there is no distributed tracing across the transaction processing pipeline — no trace IDs propagated through Kafka consumer → rule gate → scatter-gather → LLM/RAG/history → decision. No custom business metrics (fraud decision counts, latency histograms, circuit breaker state).

**Why it matters:** At the $160+/hr Staff/Principal tier, interviewers expect end-to-end trace visibility. The scatter-gather pattern with three parallel futures is exactly the kind of flow where distributed tracing provides the most value — without it, debugging production latency issues requires log correlation. The `ScopedContextFilter` already extracts a correlation ID from `X-Request-Id`, which shows awareness of the need, but it's not wired into a tracing backend.

**Estimated effort:** 1 hour

**Fix:** Add `micrometer-tracing-bridge-otel` and `opentelemetry-exporter-otlp` dependencies. Add Jaeger or Tempo to Docker Compose. Spring Boot auto-configures trace propagation through RestClient, Kafka, and JDBC. Custom spans can be added to `FraudEvaluationUseCaseImpl` to instrument each scatter-gather phase.
