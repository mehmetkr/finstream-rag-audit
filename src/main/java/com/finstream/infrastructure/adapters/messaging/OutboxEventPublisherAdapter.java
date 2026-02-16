package com.finstream.infrastructure.adapters.messaging;

import com.finstream.domain.event.TransactionEvaluated;
import com.finstream.domain.event.TransactionEvent;
import com.finstream.domain.event.TransactionReceived;
import com.finstream.domain.ports.outbound.EventPublisherPort;
import com.finstream.infrastructure.adapters.persistence.OutboxJpaRepository;
import com.finstream.infrastructure.adapters.persistence.entity.OutboxEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class OutboxEventPublisherAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisherAdapter.class);
    private static final String AGGREGATE_TYPE = "Transaction";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisherAdapter(OutboxJpaRepository outboxRepository,
                                        ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(TransactionEvent event) {
        try {
            String eventType = switch (event) {
                case TransactionReceived _ -> "TransactionReceived";
                case TransactionEvaluated _ -> "TransactionEvaluated";
            };

            String payload = switch (event) {
                case TransactionReceived(var txn, _) -> objectMapper.writeValueAsString(
                        new TransactionReceivedPayload(
                                txn.id().value().toString(),
                                txn.amount().value(),
                                txn.amount().currency().getCurrencyCode(),
                                txn.fromAccount().value(),
                                txn.toAccount().value(),
                                txn.description(),
                                txn.occurredAt().toString()
                        ));
                case TransactionEvaluated(var txn, var decision, _) -> objectMapper.writeValueAsString(
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
                        ));
            };

            var entity = new OutboxEventEntity();
            entity.setAggregateType(AGGREGATE_TYPE);
            entity.setAggregateId(event.transactionId().value().toString());
            entity.setEventType(eventType);
            entity.setPayload(payload);
            entity.setCreatedAt(Instant.now());

            outboxRepository.save(entity);
            log.info("Outbox: wrote {} for transaction {}", eventType, event.transactionId().value());
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize transaction event for outbox", e);
        }
    }

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
