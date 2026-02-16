package com.finstream.infrastructure.adapters.persistence;

import com.finstream.domain.model.Amount;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import com.finstream.domain.ports.outbound.TransactionRepository;
import com.finstream.infrastructure.adapters.persistence.entity.TransactionEntity;
import org.springframework.stereotype.Component;

import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TransactionRepositoryAdapter implements TransactionRepository {

    private final TransactionJpaRepository jpaRepository;

    public TransactionRepositoryAdapter(TransactionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Transaction save(Transaction transaction) {
        TransactionEntity entity = toEntity(transaction);
        TransactionEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Transaction> findById(TransactionId id) {
        return jpaRepository.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<Transaction> findAllByIds(List<TransactionId> ids) {
        List<UUID> uuids = ids.stream().map(TransactionId::value).toList();
        return jpaRepository.findAllById(uuids).stream().map(this::toDomain).toList();
    }

    private TransactionEntity toEntity(Transaction transaction) {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(transaction.id().value());
        entity.setAmount(transaction.amount().value());
        entity.setCurrency(transaction.amount().currency().getCurrencyCode());
        entity.setFromAccount(transaction.fromAccount().value());
        entity.setToAccount(transaction.toAccount().value());
        entity.setDescription(transaction.description());
        entity.setOccurredAt(transaction.occurredAt());
        return entity;
    }

    private Transaction toDomain(TransactionEntity entity) {
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
