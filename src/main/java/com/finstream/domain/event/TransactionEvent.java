package com.finstream.domain.event;

import com.finstream.domain.model.ids.TransactionId;

import java.time.Instant;

public sealed interface TransactionEvent
        permits TransactionReceived, TransactionEvaluated {

    TransactionId transactionId();

    Instant occurredAt();
}
