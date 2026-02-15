# ADR-0001: Hexagonal Architecture

## Status
Accepted

## Context
FinStream-RAG-Audit is a financial fraud-detection system where correctness and
maintainability are critical. The domain logic (transaction validation, risk
scoring) must be testable in isolation and must never leak infrastructure
concerns such as persistence technology, message brokers, or web frameworks.

We evaluated three structural approaches:
1. **Layered architecture** — simple, but domain layer tends to accumulate
   framework annotations over time.
2. **Clean Architecture (Uncle Bob)** — strong isolation, but introduces many
   small interfaces that add ceremony without proportional benefit at our current
   scale.
3. **Hexagonal Architecture (Ports & Adapters)** — clear inbound/outbound port
   boundaries with adapter swappability, enforced by ArchUnit.

## Decision
Adopt Hexagonal Architecture with three top-level packages:
- `domain` — pure Java records, value objects, and port interfaces. Zero
  framework imports.
- `application` — use-case orchestrators that depend only on domain ports.
- `infrastructure.adapters` — concrete implementations (Spring Web, JPA,
  Kafka) wired via dependency injection.

ArchUnit rules enforce that `domain` never imports from `application` or
`infrastructure`, guaranteeing the dependency rule at build time.

## Consequences
- **Positive:** Domain model is framework-independent and trivially unit-testable.
  Adapters can be swapped (e.g., replacing Kafka with RabbitMQ) without touching
  business logic.
- **Positive:** ArchUnit catches violations automatically during `./gradlew check`.
- **Negative:** Slightly more boilerplate for adapter mapping (entity ↔ domain).
  Acceptable given the safety requirements of financial transaction processing.
