package com.finstream.domain.ports.outbound;

import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;

import java.time.Instant;
import java.util.List;

public interface UserHistoryPort {
    int countRecentTransactions(AccountId account, Instant since);

    List<Transaction> findRecentTransactions(AccountId account, int limit);
}
