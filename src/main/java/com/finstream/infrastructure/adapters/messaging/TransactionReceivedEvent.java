package com.finstream.infrastructure.adapters.messaging;

import java.math.BigDecimal;

record TransactionReceivedEvent(
        String id,
        BigDecimal amount,
        String currency,
        String fromAccount,
        String toAccount,
        String description,
        String occurredAt
) {}
