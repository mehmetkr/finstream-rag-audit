package com.finstream.infrastructure.adapters.persistence;

import com.finstream.infrastructure.adapters.persistence.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<TransactionEntity, UUID> {
}
