package com.finstream.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransactionRequest(
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String fromAccount,
        @NotBlank String toAccount,
        String description
) {}
