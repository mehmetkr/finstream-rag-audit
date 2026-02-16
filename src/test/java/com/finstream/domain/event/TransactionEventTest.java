package com.finstream.domain.event;

import com.finstream.domain.model.Amount;
import com.finstream.domain.model.FraudDecision;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionEventTest {

    private static final TransactionId TX_ID = TransactionId.generate();
    private static final Instant NOW = Instant.now();

    private static Transaction sampleTransaction() {
        return new Transaction(
                TX_ID,
                new Amount(BigDecimal.valueOf(250), Currency.getInstance("USD")),
                new AccountId("GB1234567890"),
                new AccountId("US9876543210"),
                "Test payment",
                NOW
        );
    }

    private static FraudDecision sampleDecision() {
        return new FraudDecision(
                FraudDecision.Decision.APPROVE,
                BigDecimal.valueOf(15),
                "Low risk — known merchant",
                NOW
        );
    }

    // --- TransactionReceived ---

    @Test
    void transactionReceived_should_implement_sealed_interface() {
        TransactionEvent event = new TransactionReceived(sampleTransaction(), NOW);
        assertThat(event).isInstanceOf(TransactionEvent.class);
    }

    @Test
    void transactionReceived_should_expose_transactionId() {
        var event = new TransactionReceived(sampleTransaction(), NOW);
        assertThat(event.transactionId()).isEqualTo(TX_ID);
    }

    @Test
    void transactionReceived_should_expose_occurredAt() {
        var event = new TransactionReceived(sampleTransaction(), NOW);
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void transactionReceived_should_reject_null_transaction() {
        assertThatThrownBy(() -> new TransactionReceived(null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void transactionReceived_should_reject_null_occurredAt() {
        assertThatThrownBy(() -> new TransactionReceived(sampleTransaction(), null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- TransactionEvaluated ---

    @Test
    void transactionEvaluated_should_implement_sealed_interface() {
        TransactionEvent event = new TransactionEvaluated(sampleTransaction(), sampleDecision(), NOW);
        assertThat(event).isInstanceOf(TransactionEvent.class);
    }

    @Test
    void transactionEvaluated_should_expose_transactionId() {
        var event = new TransactionEvaluated(sampleTransaction(), sampleDecision(), NOW);
        assertThat(event.transactionId()).isEqualTo(TX_ID);
    }

    @Test
    void transactionEvaluated_should_reject_null_transaction() {
        assertThatThrownBy(() -> new TransactionEvaluated(null, sampleDecision(), NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void transactionEvaluated_should_reject_null_decision() {
        assertThatThrownBy(() -> new TransactionEvaluated(sampleTransaction(), null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void transactionEvaluated_should_reject_null_occurredAt() {
        assertThatThrownBy(() -> new TransactionEvaluated(sampleTransaction(), sampleDecision(), null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- Sealed type exhaustive switch ---

    @Test
    void exhaustive_switch_should_cover_all_event_types() {
        TransactionEvent received = new TransactionReceived(sampleTransaction(), NOW);
        TransactionEvent evaluated = new TransactionEvaluated(sampleTransaction(), sampleDecision(), NOW);

        assertThat(describeEvent(received)).isEqualTo("received");
        assertThat(describeEvent(evaluated)).isEqualTo("evaluated");
    }

    @Test
    void record_pattern_deconstruction_should_extract_components() {
        var txn = sampleTransaction();
        var decision = sampleDecision();
        TransactionEvent event = new TransactionEvaluated(txn, decision, NOW);

        // Demonstrates record pattern matching — compiler verifies exhaustiveness
        switch (event) {
            case TransactionReceived(var t, var at) -> {
                assertThat(t).isEqualTo(txn);
                assertThat(at).isEqualTo(NOW);
            }
            case TransactionEvaluated(var t, var d, var at) -> {
                assertThat(t).isEqualTo(txn);
                assertThat(d).isEqualTo(decision);
                assertThat(at).isEqualTo(NOW);
            }
        }
    }

    private static String describeEvent(TransactionEvent event) {
        return switch (event) {
            case TransactionReceived _ -> "received";
            case TransactionEvaluated _ -> "evaluated";
        };
    }
}
