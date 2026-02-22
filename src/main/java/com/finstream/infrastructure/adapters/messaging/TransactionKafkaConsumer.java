package com.finstream.infrastructure.adapters.messaging;

import com.finstream.domain.event.TransactionEvaluated;
import com.finstream.domain.event.TransactionEvent;
import com.finstream.domain.event.TransactionReceived;
import com.finstream.domain.model.Amount;
import com.finstream.domain.model.FraudDecision;
import com.finstream.domain.model.RequestContext;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import com.finstream.domain.ports.inbound.FraudEvaluationUseCase;
import com.finstream.domain.ports.outbound.EmbeddingStorePort;
import com.finstream.domain.ports.outbound.EventPublisherPort;
import com.finstream.domain.ports.outbound.TransactionRepository;
import com.finstream.infrastructure.observability.AuditTraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Component
public class TransactionKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionKafkaConsumer.class);

    private final TransactionRepository repository;
    private final EmbeddingStorePort embeddingStore;
    private final FraudEvaluationUseCase fraudEvaluationUseCase;
    private final EventPublisherPort eventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TransactionKafkaConsumer(TransactionRepository repository,
                                    EmbeddingStorePort embeddingStore,
                                    FraudEvaluationUseCase fraudEvaluationUseCase,
                                    EventPublisherPort eventPublisher,
                                    ObjectMapper objectMapper,
                                    Clock clock) {
        this.repository = repository;
        this.embeddingStore = embeddingStore;
        this.fraudEvaluationUseCase = fraudEvaluationUseCase;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    @KafkaListener(topics = "outbox.event.Transaction", groupId = "finstream-group")
    public void handle(String payload,
                       @Header(value = "eventType", required = false) String eventType) {
        String traceId = UUID.randomUUID().toString();

        RequestContext.runWithContext(traceId, "kafka-consumer", "system", () -> {
            try {
                TransactionEvent event = deserialize(payload, eventType);
                process(event, traceId);
            } catch (IllegalArgumentException e) {
                log.warn("[{}] Rejected invalid transaction event: {}", traceId, e.getMessage());
            } catch (Exception e) {
                log.error("[{}] Failed to process transaction event (poison pill dropped)", traceId, e);
            }
        });
    }

    private void process(TransactionEvent event, String traceId) {
        switch (event) {
            case TransactionReceived(var transaction, _) -> {
                repository.save(transaction);

                AuditTraceContext.setCurrent(new AuditTraceContext(
                        transaction.id().value().toString(),
                        "transaction",
                        "fraud_evaluation"
                ));

                try {
                    embeddingStore.store(transaction.id(), transaction);
                    log.info("[{}] Persisted and embedded transaction: {}", traceId, transaction.id().value());

                    FraudDecision decision = fraudEvaluationUseCase.evaluate(transaction);
                    var evaluated = new TransactionEvaluated(transaction, decision, Instant.now(clock));
                    eventPublisher.publish(evaluated);
                    log.info("[{}] Evaluated transaction {}: {} (risk: {})",
                            traceId, transaction.id().value(), decision.decision(), decision.riskScore());
                } finally {
                    AuditTraceContext.clear();
                }
            }
            case TransactionEvaluated(var transaction, var decision, _) ->
                    log.info("[{}] Audit: transaction {} \u2014 {} (risk: {})",
                            traceId, transaction.id().value(),
                            decision.decision(), decision.riskScore());
        }
    }

    private TransactionEvent deserialize(String payload, String eventType) throws Exception {
        return switch (eventType) {
            case "TransactionReceived" -> {
                var dto = objectMapper.readValue(payload, TransactionReceivedEvent.class);
                yield new TransactionReceived(
                        mapTransaction(dto.id(), dto.amount(), dto.currency(),
                                dto.fromAccount(), dto.toAccount(), dto.description(), dto.occurredAt()),
                        Instant.now(clock));
            }
            case "TransactionEvaluated" -> {
                var dto = objectMapper.readValue(payload, TransactionEvaluatedEvent.class);
                var decision = new FraudDecision(
                        FraudDecision.Decision.valueOf(dto.decision()),
                        dto.riskScore(), dto.reasoning(),
                        Instant.parse(dto.evaluatedAt()));
                yield new TransactionEvaluated(
                        mapTransaction(dto.id(), dto.amount(), dto.currency(),
                                dto.fromAccount(), dto.toAccount(), dto.description(), dto.occurredAt()),
                        decision, Instant.now(clock));
            }
            case null -> throw new IllegalArgumentException("Missing eventType header");
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }

    private Transaction mapTransaction(String id, BigDecimal amount, String currency,
                                        String fromAccount, String toAccount,
                                        String description, String occurredAt) {
        return new Transaction(
                TransactionId.from(id),
                new Amount(amount, Currency.getInstance(currency)),
                new AccountId(fromAccount), new AccountId(toAccount),
                description, Instant.parse(occurredAt));
    }
}
