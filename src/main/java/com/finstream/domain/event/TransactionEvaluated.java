package com.finstream.domain.event;

import com.finstream.domain.model.FraudDecision;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.TransactionId;

import java.time.Instant;
import java.util.Objects;

public record TransactionEvaluated(
        Transaction transaction,
        FraudDecision fraudDecision,
        Instant occurredAt
) implements TransactionEvent {

    public TransactionEvaluated {
        Objects.requireNonNull(transaction, "Transaction cannot be null");
        Objects.requireNonNull(fraudDecision, "FraudDecision cannot be null");
        Objects.requireNonNull(occurredAt, "OccurredAt cannot be null");
    }

    @Override
    public TransactionId transactionId() {
        return transaction.id();
    }
}
