package com.finstream.infrastructure.adapters.messaging;

import com.finstream.domain.event.TransactionEvaluated;
import com.finstream.domain.event.TransactionReceived;
import com.finstream.domain.model.Amount;
import com.finstream.domain.model.FraudDecision;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import com.finstream.infrastructure.adapters.persistence.OutboxJpaRepository;
import com.finstream.infrastructure.adapters.persistence.entity.OutboxEventEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherAdapterTest {

    @Mock private OutboxJpaRepository outboxRepository;

    private static final Clock TEST_CLOCK =
            Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

    private OutboxEventPublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OutboxEventPublisherAdapter(
                outboxRepository, new ObjectMapper(), TEST_CLOCK);
    }

    @Test
    void should_persist_outbox_row_for_transaction_received() {
        Transaction tx = testTransaction();
        var event = new TransactionReceived(tx, Instant.now(TEST_CLOCK));

        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adapter.publish(event);

        var captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEventEntity saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Transaction");
        assertThat(saved.getAggregateId()).isEqualTo(tx.id().value().toString());
        assertThat(saved.getEventType()).isEqualTo("TransactionReceived");
        assertThat(saved.getPayload()).contains(tx.id().value().toString());
        assertThat(saved.getPayload()).contains("GB1234567890");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void should_persist_outbox_row_for_transaction_evaluated() {
        Transaction tx = testTransaction();
        var decision = new FraudDecision(
                FraudDecision.Decision.APPROVE, BigDecimal.TEN, "Low risk", Instant.now(TEST_CLOCK));
        var event = new TransactionEvaluated(tx, decision, Instant.now(TEST_CLOCK));

        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adapter.publish(event);

        var captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEventEntity saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Transaction");
        assertThat(saved.getAggregateId()).isEqualTo(tx.id().value().toString());
        assertThat(saved.getEventType()).isEqualTo("TransactionEvaluated");
        assertThat(saved.getPayload()).contains("APPROVE");
        assertThat(saved.getPayload()).contains("Low risk");
    }

    @Test
    void should_set_aggregate_id_to_transaction_id() {
        Transaction tx = testTransaction();
        var event = new TransactionReceived(tx, Instant.now(TEST_CLOCK));

        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adapter.publish(event);

        var captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());

        assertThat(captor.getValue().getAggregateId())
                .isEqualTo(tx.id().value().toString());
    }

    private static Transaction testTransaction() {
        return new Transaction(
                TransactionId.generate(),
                new Amount(BigDecimal.valueOf(500), Currency.getInstance("USD")),
                new AccountId("GB1234567890"),
                new AccountId("US9876543210"),
                "Test payment",
                Instant.now(TEST_CLOCK)
        );
    }
}
