# Performance Benchmarks (JMH)

This document contains results from the JMH (Java Microbenchmark Harness) benchmarks for critical components.

## PiiRedactorBenchmark

Measures the throughput of the `PiiRedactor` service, which performs SHA-256 hashing and regex-based scrubbing of transaction data.

**Benchmark:** `com.finstream.benchmarks.PiiRedactorBenchmark.redact`
**Mode:** Throughput (ops/ms)
**Result:** `~303 ops/ms`

This result indicates that the PII redaction process can handle over 300,000 operations per second, confirming it is not a performance bottleneck for the transaction processing pipeline.
