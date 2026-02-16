package com.finstream.infrastructure.adapters.persistence;

import com.finstream.infrastructure.adapters.persistence.entity.TransactionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<TransactionEntity, UUID> {

    @Query("SELECT COUNT(e) FROM TransactionEntity e WHERE (e.fromAccount = :account OR e.toAccount = :account) AND e.occurredAt > :since")
    long countRecentByAccount(@Param("account") String account, @Param("since") Instant since);

    @Query("SELECT e FROM TransactionEntity e WHERE e.fromAccount = :account OR e.toAccount = :account ORDER BY e.occurredAt DESC")
    List<TransactionEntity> findByAccountOrderByOccurredAtDesc(@Param("account") String account, Pageable pageable);
}
