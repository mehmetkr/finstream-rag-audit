package com.finstream.infrastructure.adapters.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class OutboxCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupScheduler.class);

    private final OutboxJpaRepository outboxRepository;
    private final Duration retentionPeriod;

    public OutboxCleanupScheduler(OutboxJpaRepository outboxRepository,
                                   @Value("${finstream.outbox.retention:P7D}") Duration retentionPeriod) {
        this.outboxRepository = outboxRepository;
        this.retentionPeriod = retentionPeriod;
    }

    @Scheduled(cron = "${finstream.outbox.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(retentionPeriod);
        int deleted = outboxRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Outbox cleanup: deleted {} events older than {}", deleted, cutoff);
        }
    }
}
