package com.finstream.domain.model;

import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;

import java.time.Instant;
import java.util.Objects;

public record Transaction(
        TransactionId id,
        Amount amount,
        AccountId fromAccount,
        AccountId toAccount,
        String description,
        Instant occurredAt
) {
    public Transaction {
        Objects.requireNonNull(id, "Transaction ID cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(fromAccount, "From account cannot be null");
        Objects.requireNonNull(toAccount, "To account cannot be null");
        Objects.requireNonNull(description, "Description cannot be null");
        Objects.requireNonNull(occurredAt, "Occurred at cannot be null");
    }
}
