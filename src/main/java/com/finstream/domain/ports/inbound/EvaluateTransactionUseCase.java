package com.finstream.domain.ports.inbound;

import com.finstream.domain.model.Transaction;

public interface EvaluateTransactionUseCase {
    void submit(Transaction transaction);
}
