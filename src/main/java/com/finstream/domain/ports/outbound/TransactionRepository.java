package com.finstream.domain.ports.outbound;

import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.TransactionId;

import java.util.Optional;

public interface TransactionRepository {
    Transaction save(Transaction transaction);
    Optional<Transaction> findById(TransactionId id);
}
