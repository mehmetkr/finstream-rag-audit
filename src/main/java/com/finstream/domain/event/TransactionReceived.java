package com.finstream.domain.event;

import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.TransactionId;

import java.time.Instant;
import java.util.Objects;

public record TransactionReceived(
        Transaction transaction,
        Instant occurredAt
) implements TransactionEvent {

    public TransactionReceived {
        Objects.requireNonNull(transaction, "Transaction cannot be null");
        Objects.requireNonNull(occurredAt, "OccurredAt cannot be null");
    }

    @Override
    public TransactionId transactionId() {
        return transaction.id();
    }
}
