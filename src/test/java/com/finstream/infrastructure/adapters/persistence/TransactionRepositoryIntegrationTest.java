package com.finstream.infrastructure.adapters.persistence;

import com.finstream.infrastructure.adapters.persistence.entity.TransactionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("finstream")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private TransactionJpaRepository repository;

    @Test
    void should_save_and_retrieve_transaction() {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(UUID.randomUUID());
        entity.setAmount(BigDecimal.valueOf(250.00));
        entity.setCurrency("USD");
        entity.setFromAccount("GB1234567890");
        entity.setToAccount("US9876543210");
        entity.setDescription("Test payment");
        entity.setOccurredAt(Instant.now());

        TransactionEntity saved = repository.save(entity);

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findById(saved.getId())).isPresent()
                .hasValueSatisfying(tx -> {
                    assertThat(tx.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(250.00));
                    assertThat(tx.getCurrency()).isEqualTo("USD");
                    assertThat(tx.getFromAccount()).isEqualTo("GB1234567890");
                    assertThat(tx.getDescription()).isEqualTo("Test payment");
                });
    }

    @Test
    void should_return_empty_for_nonexistent_id() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }
}
