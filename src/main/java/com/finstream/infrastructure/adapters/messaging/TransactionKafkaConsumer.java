package com.finstream.infrastructure.adapters.messaging;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.finstream.domain.model.Amount;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import com.finstream.domain.model.RequestContext;
import com.finstream.domain.ports.outbound.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Component
public class TransactionKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionKafkaConsumer.class);

    private final TransactionRepository repository;
    private final ObjectMapper objectMapper;

    public TransactionKafkaConsumer(TransactionRepository repository,
                                    ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @KafkaListener(topics = "transactions.incoming", groupId = "finstream-group")
    public void handle(String payload) {
        String traceId = UUID.randomUUID().toString();

        RequestContext.runWithContext(traceId, "kafka-consumer", "system", () -> {
            try {
                JsonNode node = objectMapper.readTree(payload);

                Transaction transaction = new Transaction(
                        TransactionId.from(node.get("id").asText()),
                        new Amount(
                                new BigDecimal(node.get("amount").asText()),
                                Currency.getInstance(node.get("currency").asText())
                        ),
                        new AccountId(node.get("fromAccount").asText()),
                        new AccountId(node.get("toAccount").asText()),
                        node.has("description") ? node.get("description").asText() : null,
                        Instant.parse(node.get("occurredAt").asText())
                );

                repository.save(transaction);
                log.info("[{}] Persisted transaction: {}", traceId, transaction.id().value());
            } catch (IllegalArgumentException e) {
                // Domain validation failure (invalid account ID, negative amount, etc.)
                // Log and skip — retrying won't fix bad data
                log.warn("[{}] Rejected invalid transaction event: {}", traceId, e.getMessage());
            } catch (Exception e) {
                // Transient/unexpected error — rethrow so Spring Kafka retries
                log.error("[{}] Failed to process transaction event", traceId, e);
                throw new RuntimeException("Failed to process transaction event", e);
            }
        });
    }
}
