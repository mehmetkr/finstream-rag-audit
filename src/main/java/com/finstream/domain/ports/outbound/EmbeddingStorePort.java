package com.finstream.domain.ports.outbound;

import com.finstream.domain.model.ScoredTransaction;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.TransactionId;

import java.util.List;

public interface EmbeddingStorePort {

    void store(TransactionId id, Transaction transaction);

    List<ScoredTransaction> findSimilar(Transaction transaction, int maxResults);
}
