# ADR-0003: pgvector over Pinecone for Vector Storage

## Status
Accepted

## Context
Phase 2 requires vector similarity search for RAG-based fraud detection —
retrieving historically similar transactions to provide context for LLM-based
risk assessment. We need a vector store that can:

1. Store and query embeddings alongside transactional data.
2. Participate in ACID transactions (a fraud decision should atomically
   read embeddings and write audit results).
3. Operate without external SaaS dependencies in regulated environments
   where financial data must remain on-premises or within controlled
   infrastructure.

We evaluated three options:
1. **Pinecone** — managed SaaS, high query performance, but data leaves the
   security boundary. No ACID integration with PostgreSQL.
2. **Weaviate / Milvus** — self-hosted dedicated vector DBs. High performance
   at scale, but introduce a separate persistence layer with its own
   operational burden, backup strategy, and failure modes.
3. **pgvector** — PostgreSQL extension. Vectors co-located with transactional
   data in a single database. Full ACID, single backup, single connection pool.

## Decision
Use pgvector as the vector store, running as an extension in the existing
PostgreSQL 16 instance.

Embeddings are stored in the same database as transactions, enabling queries
like:

```sql
SELECT t.*, e.embedding <=> $1 AS distance
FROM transactions t
JOIN transaction_embeddings e ON t.id = e.transaction_id
WHERE e.embedding <=> $1 < 0.3
ORDER BY distance
LIMIT 10;
```

This query participates in the same transaction as the fraud evaluation write,
ensuring consistency.

## Consequences
- **Positive:** Single database to operate, back up, and monitor. No additional
  infrastructure. ACID-compliant vector reads within fraud evaluation
  transactions.
- **Positive:** No SaaS dependency — all data stays within the deployment
  boundary. Simplifies compliance in regulated financial environments.
- **Positive:** Docker Compose already runs `pgvector/pgvector:pg16`, so
  development and CI environments work out of the box.
- **Negative:** pgvector's ANN (approximate nearest neighbor) performance
  degrades beyond ~10M vectors compared to purpose-built vector databases.
  Acceptable for our scale (transaction similarity within a tenant, not
  global search). If scale demands it, we can migrate to a dedicated
  store behind the same repository port — the hexagonal architecture
  makes this a single-adapter swap.
- **Negative:** Embedding generation adds latency to the ingestion path.
  Mitigated by computing embeddings asynchronously via the Kafka consumer,
  not in the synchronous HTTP request path.
