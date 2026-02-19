package com.finstream.infrastructure.adapters.messaging;

import com.finstream.domain.model.FraudDecision;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.TransactionId;
import com.finstream.domain.ports.inbound.FraudEvaluationUseCase;
import com.finstream.domain.ports.outbound.EmbeddingStorePort;
import com.finstream.domain.ports.outbound.EventPublisherPort;
import com.finstream.domain.ports.outbound.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionKafkaConsumerTest {

    @Mock TransactionRepository repository;
    @Mock EmbeddingStorePort embeddingStore;
    @Mock FraudEvaluationUseCase fraudEvaluationUseCase;
    @Mock EventPublisherPort eventPublisher;

    private TransactionKafkaConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        consumer = new TransactionKafkaConsumer(
                repository, embeddingStore, fraudEvaluationUseCase,
                eventPublisher, objectMapper, clock);
    }

    @Test
    void handle_TransactionReceived_should_process_correctly() throws Exception {
        String id = UUID.randomUUID().toString();
        String json = """
                {
                    "id": "%s",
                    "amount": 100.00,
                    "currency": "USD",
                    "fromAccount": "US1234567890",
                    "toAccount": "GB9876543210",
                    "description": "Test",
                    "occurredAt": "%s"
                }
                """.formatted(id, Instant.now(clock));

        when(fraudEvaluationUseCase.evaluate(any())).thenReturn(
                new FraudDecision(FraudDecision.Decision.APPROVE, BigDecimal.TEN, "OK", Instant.now(clock)));

        consumer.handle(json, "TransactionReceived");

        verify(repository).save(any(Transaction.class));
        verify(embeddingStore).store(eq(new TransactionId(UUID.fromString(id))), any(Transaction.class));
        verify(fraudEvaluationUseCase).evaluate(any(Transaction.class));
        verify(eventPublisher).publish(any());
    }

    @Test
    void handle_TransactionEvaluated_should_log_only() {
        String json = """
                {
                    "id": "%s",
                    "amount": 100.00,
                    "currency": "USD",
                    "fromAccount": "US1234567890",
                    "toAccount": "GB9876543210",
                    "description": "Test",
                    "occurredAt": "%s",
                    "decision": "APPROVE",
                    "riskScore": 10.0,
                    "reasoning": "OK",
                    "evaluatedAt": "%s"
                }
                """.formatted(UUID.randomUUID(), Instant.now(clock), Instant.now(clock));

        consumer.handle(json, "TransactionEvaluated");

        verifyNoInteractions(repository);
        verifyNoInteractions(embeddingStore);
        verifyNoInteractions(fraudEvaluationUseCase);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void handle_InvalidEvent_should_not_throw() {
        consumer.handle("invalid json", "TransactionReceived");
        // No exception thrown
    }
}
