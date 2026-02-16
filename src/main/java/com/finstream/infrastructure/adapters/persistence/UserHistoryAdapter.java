package com.finstream.infrastructure.adapters.persistence;

import com.finstream.domain.model.Amount;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import com.finstream.domain.ports.outbound.UserHistoryPort;
import com.finstream.infrastructure.adapters.persistence.entity.TransactionEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Currency;
import java.util.List;

@Component
public class UserHistoryAdapter implements UserHistoryPort {

    private final TransactionJpaRepository jpaRepository;

    public UserHistoryAdapter(TransactionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public int countRecentTransactions(AccountId account, Instant since) {
        return (int) jpaRepository.countRecentByAccount(account.value(), since);
    }

    @Override
    public List<Transaction> findRecentTransactions(AccountId account, int limit) {
        return jpaRepository.findByAccountOrderByOccurredAtDesc(account.value(), PageRequest.ofSize(limit))
                .stream()
                .map(UserHistoryAdapter::toDomain)
                .toList();
    }

    private static Transaction toDomain(TransactionEntity entity) {
        return new Transaction(
                new TransactionId(entity.getId()),
                new Amount(entity.getAmount(), Currency.getInstance(entity.getCurrency())),
                new AccountId(entity.getFromAccount()),
                new AccountId(entity.getToAccount()),
                entity.getDescription(),
                entity.getOccurredAt()
        );
    }
}
