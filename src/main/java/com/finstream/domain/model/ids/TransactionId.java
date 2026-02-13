package com.finstream.domain.model.ids;

import java.util.Objects;
import java.util.UUID;

public record TransactionId(UUID value) {

    public TransactionId {
        Objects.requireNonNull(value, "TransactionId cannot be null");
    }

    public static TransactionId generate() {
        return new TransactionId(UUID.randomUUID());
    }

    public static TransactionId from(String value) {
        return new TransactionId(UUID.fromString(value));
    }
}
