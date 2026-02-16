package com.finstream.domain.model;

import com.finstream.domain.model.ids.TransactionId;

import java.time.Instant;
import java.util.Objects;

public record RedactedTransaction(
        TransactionId id,
        Amount amount,
        RedactedAccountId redactedFromAccount,
        RedactedAccountId redactedToAccount,
        String redactedDescription,
        Instant occurredAt
) {

    public RedactedTransaction {
        Objects.requireNonNull(id, "Transaction ID cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(redactedFromAccount, "Redacted from account cannot be null");
        Objects.requireNonNull(redactedToAccount, "Redacted to account cannot be null");
        Objects.requireNonNull(redactedDescription, "Redacted description cannot be null");
        Objects.requireNonNull(occurredAt, "Occurred at cannot be null");
    }
}
