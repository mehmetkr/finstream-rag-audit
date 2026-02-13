package com.finstream.infrastructure.adapters.messaging;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.finstream.infrastructure.adapters.persistence.TransactionJpaRepository;
import com.finstream.infrastructure.adapters.persistence.entity.TransactionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
public class TransactionKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionKafkaConsumer.class);

    private final TransactionJpaRepository repository;
    private final ObjectMapper objectMapper;

    public TransactionKafkaConsumer(TransactionJpaRepository repository,
                                    ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "transactions.incoming", groupId = "finstream-group")
    public void handle(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);

            TransactionEntity entity = new TransactionEntity();
            entity.setId(UUID.fromString(node.get("id").asText()));
            entity.setAmount(new BigDecimal(node.get("amount").asText()));
            entity.setCurrency(node.get("currency").asText());
            entity.setFromAccount(node.get("fromAccount").asText());
            entity.setToAccount(node.get("toAccount").asText());
            entity.setDescription(node.has("description") ? node.get("description").asText() : null);
            entity.setOccurredAt(Instant.parse(node.get("occurredAt").asText()));

            repository.save(entity);
            log.info("Persisted transaction: {}", entity.getId());
        } catch (Exception e) {
            log.error("Failed to process transaction event", e);
            throw new RuntimeException("Failed to process transaction event", e);
        }
    }
}
