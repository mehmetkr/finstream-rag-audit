package com.finstream.infrastructure.adapters.messaging;

import com.finstream.infrastructure.adapters.persistence.TransactionJpaRepository;
import com.finstream.infrastructure.adapters.persistence.entity.TransactionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

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

    @Test
    void should_consume_transaction_event_and_persist() {
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

        kafkaTemplate.send("transactions.incoming", txId.toString(), payload);

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
}
