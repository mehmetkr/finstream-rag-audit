package com.finstream.infrastructure.adapters.messaging;

import java.math.BigDecimal;

record TransactionEvaluatedEvent(
        String id,
        BigDecimal amount,
        String currency,
        String fromAccount,
        String toAccount,
        String description,
        String occurredAt,
        String decision,
        BigDecimal riskScore,
        String reasoning,
        String evaluatedAt
) {}
