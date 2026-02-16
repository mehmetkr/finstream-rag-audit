package com.finstream.infrastructure.adapters.persistence;

import com.finstream.infrastructure.adapters.persistence.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Modifying
    @Query("DELETE FROM OutboxEventEntity e WHERE e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
