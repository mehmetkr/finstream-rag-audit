package com.finstream.benchmarks;

import com.finstream.domain.model.Amount;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import com.finstream.domain.service.PiiRedactor;
import org.openjdk.jmh.annotations.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class PiiRedactorBenchmark {

    private PiiRedactor redactor;
    private Transaction transaction;

    @Setup
    public void setup() {
        redactor = new PiiRedactor();
        transaction = new Transaction(
                TransactionId.generate(),
                new Amount(BigDecimal.valueOf(100), Currency.getInstance("USD")),
                new AccountId("US1234567890"),
                new AccountId("GB0987654321"),
                "Payment to john.doe@example.com for services, SSN 123-45-6789 verified",
                Instant.now()
        );
    }

    @Benchmark
    public void redact() {
        redactor.redact(transaction);
    }
}
