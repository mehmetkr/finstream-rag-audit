# ADR-0004: Transactional Outbox with Debezium CDC

## Status
Accepted

## Context
The event publisher was writing directly to Kafka via `KafkaTemplate` after
persisting transactions to PostgreSQL. This dual-write pattern creates a
consistency risk: if the Kafka send fails after the DB commit, the event is
silently lost. Conversely, if the application crashes between the DB commit and
the Kafka send, the transaction exists in the database but no downstream
consumer is notified.

In a financial system, event loss can mean missed fraud evaluations or
incomplete audit trails — both unacceptable.

We evaluated three approaches:
1. **Kafka Transactions (exactly-once)** — ties the application to Kafka as both
   the source of truth and the messaging layer. Adds significant complexity and
   couples the domain to Kafka internals.
2. **Listen-to-yourself (polling publisher)** — a background job polls an outbox
   table and publishes to Kafka. Simple, but introduces latency proportional to
   the poll interval and requires idempotency logic.
3. **Transactional Outbox + Debezium CDC** — events are written to an `outbox_events`
   table within the same database transaction. Debezium captures these writes
   from the PostgreSQL WAL and routes them to Kafka via the EventRouter SMT.

## Decision
Adopt the Transactional Outbox pattern with Debezium CDC.

- `OutboxEventPublisherAdapter` replaces `EventPublisherKafkaAdapter`. It
  implements `EventPublisherPort` by inserting into the `outbox_events` table
  within the same `@Transactional` boundary as the business operation.
- Debezium Kafka Connect reads the PostgreSQL WAL (`wal_level=logical`, already
  configured) and applies the `io.debezium.transforms.outbox.EventRouter` SMT
  to route events by `aggregate_type` to topic `outbox.event.{AggregateType}`.
- The consumer reads from the Debezium-managed topic and uses the Kafka header
  `eventType` (set by EventRouter from the `event_type` column) to determine
  deserialization.
- A scheduled cleanup job purges outbox rows older than 7 days. This is safe
  because Debezium reads from the WAL, not the table.

## Consequences
- **Positive:** Event publishing is now atomic with the business transaction.
  No event loss or ghost events.
- **Positive:** The domain and application layers are unchanged — only the
  infrastructure adapter was swapped, validating the hexagonal architecture.
- **Positive:** Debezium provides near-real-time event delivery (~seconds)
  without application-level polling.
- **Negative:** Adds operational complexity — Kafka Connect must be deployed
  and monitored alongside the application.
- **Negative:** The outbox table grows until purged. The scheduled cleanup
  mitigates this, but monitoring table size is recommended.
- **Negative:** Local development now requires three services (Postgres, Kafka,
  Kafka Connect) instead of two.
