package com.finstream.infrastructure.adapters.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.ports.outbound.EventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisherKafkaAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(EventPublisherKafkaAdapter.class);
    private static final String TOPIC = "transactions.incoming";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EventPublisherKafkaAdapter(KafkaTemplate<String, String> kafkaTemplate,
                                       ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishTransactionReceived(Transaction transaction) {
        try {
            String payload = objectMapper.writeValueAsString(new TransactionEvent(
                    transaction.id().value().toString(),
                    transaction.amount().value(),
                    transaction.amount().currency().getCurrencyCode(),
                    transaction.fromAccount().value(),
                    transaction.toAccount().value(),
                    transaction.description(),
                    transaction.occurredAt().toString()
            ));
            kafkaTemplate.send(TOPIC, transaction.id().value().toString(), payload);
            log.info("Published transaction event: {}", transaction.id().value());
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize transaction event", e);
        }
    }

    private record TransactionEvent(
            String id, java.math.BigDecimal amount, String currency,
            String fromAccount, String toAccount, String description, String occurredAt
    ) {}
}
