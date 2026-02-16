package com.finstream.domain.model;

public record RedactedAccountId(String value) {

    public RedactedAccountId {
        if (value == null || !value.matches("^[0-9a-f]{8}$")) {
            throw new IllegalArgumentException("Redacted account ID must be 8-char lowercase hex, got: " + value);
        }
    }
}
