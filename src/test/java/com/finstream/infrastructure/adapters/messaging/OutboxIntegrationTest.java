package com.finstream.infrastructure.adapters.messaging;

import com.finstream.domain.event.TransactionReceived;
import com.finstream.domain.model.Amount;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import com.finstream.domain.ports.outbound.EventPublisherPort;
import com.finstream.infrastructure.adapters.persistence.OutboxJpaRepository;
import com.finstream.infrastructure.adapters.persistence.TransactionJpaRepository;
import com.finstream.infrastructure.adapters.persistence.entity.TransactionEntity;
import com.finstream.testsupport.TracerTestUtils;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("finstream")
            .withUsername("test")
            .withPassword("test");

    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            "confluentinc/cp-kafka:7.5.0"
    );

    @Autowired
    private EventPublisherPort eventPublisher;

    @Autowired
    private TransactionJpaRepository transactionRepository;

    @Autowired
    private OutboxJpaRepository outboxRepository;
    @MockitoBean
    private OpenTelemetry openTelemetry;

    @BeforeEach
    void setUpTracer() {
        TracerTestUtils.stubOpenTelemetry(openTelemetry);
    }

    @Test
    @Transactional
    void should_write_outbox_row_atomically_with_transaction_persist() {
        Transaction tx = testTransaction();

        // Simulate what the consumer does: persist + publish in same transaction
        TransactionEntity entity = toEntity(tx);
        transactionRepository.save(entity);
        eventPublisher.publish(new TransactionReceived(tx, Instant.now()));

        // Both rows should exist within the same transaction
        assertThat(transactionRepository.findById(tx.id().value())).isPresent();

        var outboxRows = outboxRepository.findAll();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.getFirst().getAggregateId())
                .isEqualTo(tx.id().value().toString());
        assertThat(outboxRows.getFirst().getEventType())
                .isEqualTo("TransactionReceived");
        assertThat(outboxRows.getFirst().getPayload())
                .contains(tx.id().value().toString());
    }

    @Test
    void should_persist_outbox_row_for_standalone_publish() {
        Transaction tx = testTransaction();

        eventPublisher.publish(new TransactionReceived(tx, Instant.now()));

        var outboxRows = outboxRepository.findAll();
        assertThat(outboxRows).isNotEmpty();
        assertThat(outboxRows.stream()
                .anyMatch(row -> row.getAggregateId().equals(tx.id().value().toString())))
                .isTrue();
    }

    private static Transaction testTransaction() {
        return new Transaction(
                TransactionId.generate(),
                new Amount(BigDecimal.valueOf(500), Currency.getInstance("USD")),
                new AccountId("GB1234567890"),
                new AccountId("US9876543210"),
                "Outbox integration test",
                Instant.now()
        );
    }

    private static TransactionEntity toEntity(Transaction tx) {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(tx.id().value());
        entity.setAmount(tx.amount().value());
        entity.setCurrency(tx.amount().currency().getCurrencyCode());
        entity.setFromAccount(tx.fromAccount().value());
        entity.setToAccount(tx.toAccount().value());
        entity.setDescription(tx.description());
        entity.setOccurredAt(tx.occurredAt());
        return entity;
    }
}
