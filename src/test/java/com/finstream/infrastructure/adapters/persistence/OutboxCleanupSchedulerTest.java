package com.finstream.infrastructure.adapters.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupSchedulerTest {

    @Mock OutboxJpaRepository repository;

    @Test
    void cleanup_should_delete_old_events() {
        OutboxCleanupScheduler scheduler = new OutboxCleanupScheduler(repository, Duration.ofDays(7));
        when(repository.deleteByCreatedAtBefore(any(Instant.class))).thenReturn(5);

        scheduler.cleanup();

        verify(repository).deleteByCreatedAtBefore(any(Instant.class));
    }
}
