package com.finstream.infrastructure.adapters.messaging;

import com.finstream.infrastructure.adapters.persistence.TransactionJpaRepository;
import com.finstream.testsupport.TracerTestUtils;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class KafkaIntegrationTest {

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
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TransactionJpaRepository transactionRepository;
    @MockitoBean
    private OpenTelemetry openTelemetry;

    @BeforeEach
    void setUpTracer() {
        TracerTestUtils.stubOpenTelemetry(openTelemetry);
    }

    @Test
    void should_consume_valid_transaction_event_and_persist() {
        UUID txId = UUID.randomUUID();
        String payload = """
                {
                    "id": "%s",
                    "amount": 100.00,
                    "currency": "USD",
                    "fromAccount": "GB1234567890",
                    "toAccount": "US9876543210",
                    "description": "Kafka test payment",
                    "occurredAt": "2026-02-13T00:00:00Z"
                }
                """.formatted(txId);

        var record = new ProducerRecord<>("outbox.event.Transaction", txId.toString(), payload);
        record.headers().add("eventType", "TransactionReceived".getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record);

        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    var saved = transactionRepository.findById(txId);
                    assertThat(saved).isPresent()
                            .hasValueSatisfying(tx -> {
                                assertThat(tx.getDescription()).isEqualTo("Kafka test payment");
                                assertThat(tx.getCurrency()).isEqualTo("USD");
                            });
                });
    }

    @Test
    void should_reject_event_with_invalid_account_id() {
        UUID txId = UUID.randomUUID();
        String payload = """
                {
                    "id": "%s",
                    "amount": 100.00,
                    "currency": "USD",
                    "fromAccount": "invalid-account",
                    "toAccount": "US9876543210",
                    "description": "Invalid account test",
                    "occurredAt": "2026-02-13T00:00:00Z"
                }
                """.formatted(txId);

        var record = new ProducerRecord<>("outbox.event.Transaction", txId.toString(), payload);
        record.headers().add("eventType", "TransactionReceived".getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record);

        // Allow time for the message to be processed (and rejected)
        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        assertThat(transactionRepository.findById(txId)).isEmpty()
                );
    }

    @Test
    void should_reject_event_with_negative_amount() {
        UUID txId = UUID.randomUUID();
        String payload = """
                {
                    "id": "%s",
                    "amount": -500.00,
                    "currency": "USD",
                    "fromAccount": "GB1234567890",
                    "toAccount": "US9876543210",
                    "description": "Negative amount test",
                    "occurredAt": "2026-02-13T00:00:00Z"
                }
                """.formatted(txId);

        var record = new ProducerRecord<>("outbox.event.Transaction", txId.toString(), payload);
        record.headers().add("eventType", "TransactionReceived".getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record);

        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        assertThat(transactionRepository.findById(txId)).isEmpty()
                );
    }
}
