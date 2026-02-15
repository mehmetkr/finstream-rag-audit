# ADR-0002: CompletableFuture + Virtual Threads over StructuredTaskScope

## Status
Accepted

## Context
Phase 2 of FinStream requires parallel fan-out for fraud evaluation: multiple
rule engines and an RAG retrieval step run concurrently, and their results are
aggregated into a final risk score. We need a concurrency model that:

1. Scales to thousands of in-flight transactions without thread-pool tuning.
2. Uses production-stable APIs (no preview features in the hot path).
3. Supports structured error propagation and timeout enforcement.

Java 25 ships Virtual Threads as a final feature (JEP 444, delivered in JDK 21)
and StructuredTaskScope as a preview API (JEP 480).

## Decision
Use `CompletableFuture` composed with a Virtual-Thread-backed executor for
parallel fan-out in the fraud evaluation pipeline:

```java
var executor = Executors.newVirtualThreadPerTaskExecutor();
var ruleFuture = CompletableFuture.supplyAsync(() -> ruleEngine.evaluate(tx), executor);
var ragFuture  = CompletableFuture.supplyAsync(() -> ragService.retrieve(tx), executor);
var combined   = ruleFuture.thenCombine(ragFuture, this::aggregate);
```

This gives us Virtual Thread scalability with a stable, non-preview API surface.

We will re-evaluate StructuredTaskScope when it exits preview, gated behind a
feature flag so the migration is a single-point change.

## Consequences
- **Positive:** Zero thread-pool tuning — Virtual Threads scale automatically.
- **Positive:** `CompletableFuture` is final, well-documented, and widely
  understood. No `--enable-preview` flag required in production.
- **Negative:** `CompletableFuture` does not enforce structured concurrency
  (child tasks can outlive the parent). We mitigate this with explicit
  `orTimeout()` calls and careful cancellation propagation.
- **Negative:** When StructuredTaskScope finalizes, a migration will be needed.
  The executor abstraction keeps the blast radius small.
