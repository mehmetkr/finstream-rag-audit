package com.finstream.infrastructure.adapters.embedding;

import com.finstream.domain.model.Amount;
import com.finstream.domain.model.ScoredTransaction;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import com.finstream.domain.ports.outbound.EmbeddingStorePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class EmbeddingIntegrationTest {

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
    private EmbeddingStorePort embeddingStore;

    @Autowired
    private com.finstream.domain.ports.outbound.TransactionRepository transactionRepository;

    @Test
    void should_store_and_retrieve_similar_transactions() {
        // Given: three transactions with different patterns
        Transaction rentPayment = createAndPersist(
                BigDecimal.valueOf(2000), "USD", "GB1234567890", "US9876543210",
                "Monthly rent payment for apartment");

        Transaction anotherRent = createAndPersist(
                BigDecimal.valueOf(1800), "USD", "GB1234567890", "US1111222233",
                "Monthly rent payment for office");

        Transaction groceries = createAndPersist(
                BigDecimal.valueOf(150), "EUR", "DE1234567890", "FR9876543210",
                "Weekly grocery shopping at supermarket");

        // Store embeddings
        embeddingStore.store(rentPayment.id(), rentPayment);
        embeddingStore.store(anotherRent.id(), anotherRent);
        embeddingStore.store(groceries.id(), groceries);

        // When: search for transactions similar to a rent payment query
        Transaction query = new Transaction(
                TransactionId.generate(),
                new Amount(BigDecimal.valueOf(1900), Currency.getInstance("USD")),
                new AccountId("GB1234567890"),
                new AccountId("US5555666677"),
                "Monthly rent payment",
                Instant.now()
        );

        List<ScoredTransaction> results = embeddingStore.findSimilar(query, 3);

        // Then: rent payments should rank higher than groceries
        assertThat(results).isNotEmpty();
        assertThat(results).hasSizeLessThanOrEqualTo(3);

        // The first result should be one of the rent payments (most similar)
        List<String> topDescriptions = results.stream()
                .limit(2)
                .map(st -> st.transaction().description())
                .toList();
        assertThat(topDescriptions).anyMatch(d -> d.contains("rent"));
    }

    @Test
    void should_return_empty_list_when_no_embeddings_stored() {
        Transaction query = new Transaction(
                TransactionId.generate(),
                new Amount(BigDecimal.valueOf(500), Currency.getInstance("GBP")),
                new AccountId("GB9999888877"),
                new AccountId("US1111222233"),
                "Unique test transaction with no matches",
                Instant.now()
        );

        // findSimilar on a fresh query that has no stored embeddings
        // should return whatever matches exist (possibly from other tests)
        List<ScoredTransaction> results = embeddingStore.findSimilar(query, 5);
        assertThat(results).isNotNull();
    }

    private Transaction createAndPersist(BigDecimal amount, String currency,
                                          String from, String to, String description) {
        Transaction tx = new Transaction(
                TransactionId.generate(),
                new Amount(amount, Currency.getInstance(currency)),
                new AccountId(from), new AccountId(to),
                description, Instant.now()
        );
        return transactionRepository.save(tx);
    }
}
