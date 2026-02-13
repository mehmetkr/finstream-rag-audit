package com.finstream.domain.model.ids;

public record AccountId(String value) {

    public AccountId {
        if (value == null || !value.matches("^[A-Z]{2}\\d{10}$")) {
            throw new IllegalArgumentException("Invalid account ID format: " + value);
        }
    }
}
