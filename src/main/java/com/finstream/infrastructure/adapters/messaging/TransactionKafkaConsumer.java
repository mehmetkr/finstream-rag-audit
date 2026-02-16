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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
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

    public TransactionKafkaConsumer(TransactionRepository repository,
                                    EmbeddingStorePort embeddingStore,
                                    FraudEvaluationUseCase fraudEvaluationUseCase,
                                    EventPublisherPort eventPublisher,
                                    ObjectMapper objectMapper) {
        this.repository = repository;
        this.embeddingStore = embeddingStore;
        this.fraudEvaluationUseCase = fraudEvaluationUseCase;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @KafkaListener(topics = {"transactions.incoming", "transactions.evaluated"}, groupId = "finstream-group")
    public void handle(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        String traceId = UUID.randomUUID().toString();

        RequestContext.runWithContext(traceId, "kafka-consumer", "system", () -> {
            try {
                TransactionEvent event = deserialize(payload, topic);
                process(event, traceId);
            } catch (IllegalArgumentException e) {
                log.warn("[{}] Rejected invalid transaction event: {}", traceId, e.getMessage());
            } catch (Exception e) {
                log.error("[{}] Failed to process transaction event", traceId, e);
                throw new RuntimeException("Failed to process transaction event", e);
            }
        });
    }

    private void process(TransactionEvent event, String traceId) {
        switch (event) {
            case TransactionReceived(var transaction, _) -> {
                repository.save(transaction);
                embeddingStore.store(transaction.id(), transaction);
                log.info("[{}] Persisted and embedded transaction: {}", traceId, transaction.id().value());

                FraudDecision decision = fraudEvaluationUseCase.evaluate(transaction);
                var evaluated = new TransactionEvaluated(transaction, decision, Instant.now());
                eventPublisher.publish(evaluated);
                log.info("[{}] Evaluated transaction {}: {} (risk: {})",
                        traceId, transaction.id().value(), decision.decision(), decision.riskScore());
            }
            case TransactionEvaluated(var transaction, var decision, _) ->
                    log.info("[{}] Audit: transaction {} \u2014 {} (risk: {})",
                            traceId, transaction.id().value(),
                            decision.decision(), decision.riskScore());
        }
    }

    private TransactionEvent deserialize(String payload, String topic) throws Exception {
        return switch (topic) {
            case "transactions.incoming" -> {
                var dto = objectMapper.readValue(payload, TransactionReceivedEvent.class);
                yield new TransactionReceived(
                        mapTransaction(dto.id(), dto.amount(), dto.currency(),
                                dto.fromAccount(), dto.toAccount(), dto.description(), dto.occurredAt()),
                        Instant.now());
            }
            case "transactions.evaluated" -> {
                var dto = objectMapper.readValue(payload, TransactionEvaluatedEvent.class);
                var decision = new FraudDecision(
                        FraudDecision.Decision.valueOf(dto.decision()),
                        dto.riskScore(), dto.reasoning(),
                        Instant.parse(dto.evaluatedAt()));
                yield new TransactionEvaluated(
                        mapTransaction(dto.id(), dto.amount(), dto.currency(),
                                dto.fromAccount(), dto.toAccount(), dto.description(), dto.occurredAt()),
                        decision, Instant.now());
            }
            default -> throw new IllegalArgumentException("Unknown topic: " + topic);
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
