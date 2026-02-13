package com.finstream.domain.model;

import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;

import java.time.Instant;

public record Transaction(
        TransactionId id,
        Amount amount,
        AccountId fromAccount,
        AccountId toAccount,
        String description,
        Instant occurredAt
) {}
