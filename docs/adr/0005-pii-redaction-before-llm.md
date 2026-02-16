# ADR-0005: Irreversible PII Redaction Before LLM Processing

## Status
Accepted

## Context
The fraud evaluation pipeline sends transaction data to an LLM for risk
analysis. Transactions contain personally identifiable information (PII):
account IDs (format `XX0000000000`), and free-text descriptions that may
include email addresses, phone numbers, and Social Security Numbers.

Sending raw PII to an external language model creates data privacy risks:
- Model providers may log or retain input data for training.
- A prompt injection or model misconfiguration could leak PII in responses.
- Regulatory frameworks (GDPR, CCPA, PCI DSS) restrict sharing financial
  PII with third-party processors without explicit controls.

We considered three approaches:
1. **Reversible tokenization** — replace PII with tokens, maintain a lookup
   table to reverse the mapping. Simpler for downstream analysis but creates
   a high-value target (the lookup table) and adds key management complexity.
2. **Encryption-based masking** — encrypt PII fields before sending to the
   LLM. The ciphertext is meaningless to the model, degrading analysis
   quality, and key rotation adds operational burden.
3. **Irreversible hashing + regex scrubbing** — SHA-256 hash account IDs
   (keeping only an 8-character hex prefix), regex-scrub known PII patterns
   from descriptions. No reversal possible; original PII accessible only
   through the primary transaction table.

## Decision
Adopt irreversible PII redaction as a domain service (`PiiRedactor`).

- Account IDs are replaced with the first 8 hex characters of their SHA-256
  hash, wrapped in a `RedactedAccountId` value object. The hash is
  deterministic (same account always produces the same prefix), preserving
  the model's ability to correlate transactions by account without exposing
  the original identifier.
- Description text is scrubbed using regex patterns for emails, phone
  numbers, and SSNs. Matched segments are replaced with sentinel tokens
  (`[REDACTED_EMAIL]`, `[REDACTED_PHONE]`, `[REDACTED_SSN]`).
- Amount and currency are preserved — they carry no PII and are essential
  for risk scoring.
- The `LlmFraudAnalysisPort` signature accepts `RedactedTransaction` (not
  raw `Transaction`), making it a compile-time invariant that PII never
  reaches the LLM boundary.
- Redaction occurs in the application layer (`FraudEvaluationUseCaseImpl`)
  before the LLM call. The RAG similarity search continues to use raw
  transaction data since it operates within the trusted system boundary.
- `PiiRedactor` is a domain service with no Spring or infrastructure
  dependencies, consistent with the hexagonal architecture.

## Consequences
- **Positive:** PII never leaves the system boundary. The compile-time port
  signature enforces this invariant — passing a raw `Transaction` to the LLM
  port is a type error.
- **Positive:** Deterministic hashing preserves account correlation for the
  LLM's pattern recognition without exposing real identifiers.
- **Positive:** No key management, no lookup tables, no encryption overhead.
- **Positive:** Regex patterns are extensible — new PII patterns (e.g.,
  passport numbers, IBANs) can be added to `PiiRedactor` without changing
  the port or adapter contracts.
- **Negative:** Irreversibility means LLM-generated insights cannot be
  traced back to specific accounts without joining against the primary
  transaction table using the transaction ID.
- **Negative:** Regex-based scrubbing may have false positives (e.g.,
  numeric sequences resembling SSNs) or false negatives (novel PII formats).
  Production deployments should consider a secondary NER-based scrubber.
- **Negative:** The 8-character hash prefix has a theoretical collision
  space of ~4 billion values. For the expected account population this is
  acceptable, but uniqueness is not guaranteed.
