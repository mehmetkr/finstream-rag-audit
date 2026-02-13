package com.finstream.domain.model;

import java.math.BigDecimal;
import java.util.Currency;

public record Amount(BigDecimal value, Currency currency) {

    public Amount {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }
}
