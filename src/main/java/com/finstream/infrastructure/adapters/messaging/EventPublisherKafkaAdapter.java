package com.finstream.infrastructure.adapters.messaging;

import com.finstream.domain.event.TransactionEvaluated;
import com.finstream.domain.event.TransactionEvent;
import com.finstream.domain.event.TransactionReceived;
import com.finstream.domain.ports.outbound.EventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

@Component
public class EventPublisherKafkaAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(EventPublisherKafkaAdapter.class);
    private static final String TOPIC_INCOMING = "transactions.incoming";
    private static final String TOPIC_EVALUATED = "transactions.evaluated";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EventPublisherKafkaAdapter(KafkaTemplate<String, String> kafkaTemplate,
                                       ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(TransactionEvent event) {
        try {
            var routed = switch (event) {
                case TransactionReceived(var txn, _) -> new RoutedPayload(
                        TOPIC_INCOMING,
                        new TransactionReceivedPayload(
                                txn.id().value().toString(),
                                txn.amount().value(),
                                txn.amount().currency().getCurrencyCode(),
                                txn.fromAccount().value(),
                                txn.toAccount().value(),
                                txn.description(),
                                txn.occurredAt().toString()
                        )
                );
                case TransactionEvaluated(var txn, var decision, _) -> new RoutedPayload(
                        TOPIC_EVALUATED,
                        new TransactionEvaluatedPayload(
                                txn.id().value().toString(),
                                txn.amount().value(),
                                txn.amount().currency().getCurrencyCode(),
                                txn.fromAccount().value(),
                                txn.toAccount().value(),
                                txn.description(),
                                txn.occurredAt().toString(),
                                decision.decision().name(),
                                decision.riskScore(),
                                decision.reasoning(),
                                decision.evaluatedAt().toString()
                        )
                );
            };

            String payload = objectMapper.writeValueAsString(routed.payload());
            String key = event.transactionId().value().toString();
            kafkaTemplate.send(routed.topic(), key, payload);
            log.info("Published {} for transaction: {}",
                    event.getClass().getSimpleName(), event.transactionId().value());
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize transaction event", e);
        }
    }

    private record RoutedPayload(String topic, Object payload) {}

    private record TransactionReceivedPayload(
            String id, BigDecimal amount, String currency,
            String fromAccount, String toAccount, String description, String occurredAt
    ) {}

    private record TransactionEvaluatedPayload(
            String id, BigDecimal amount, String currency,
            String fromAccount, String toAccount, String description, String occurredAt,
            String decision, BigDecimal riskScore, String reasoning, String evaluatedAt
    ) {}
}
