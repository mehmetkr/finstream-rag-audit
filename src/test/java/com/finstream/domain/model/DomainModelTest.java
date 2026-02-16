package com.finstream.domain.model;

import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainModelTest {

    // --- TransactionId ---

    @Test
    void transactionId_should_wrap_uuid() {
        UUID uuid = UUID.randomUUID();
        TransactionId id = new TransactionId(uuid);
        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    void transactionId_should_reject_null() {
        assertThatThrownBy(() -> new TransactionId(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void transactionId_generate_should_produce_unique_ids() {
        TransactionId id1 = TransactionId.generate();
        TransactionId id2 = TransactionId.generate();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void transactionId_from_string_should_parse_valid_uuid() {
        UUID uuid = UUID.randomUUID();
        TransactionId id = TransactionId.from(uuid.toString());
        assertThat(id.value()).isEqualTo(uuid);
    }

    // --- AccountId ---

    @Test
    void accountId_should_accept_valid_format() {
        AccountId accountId = new AccountId("GB1234567890");
        assertThat(accountId.value()).isEqualTo("GB1234567890");
    }

    @Test
    void accountId_should_reject_invalid_format() {
        assertThatThrownBy(() -> new AccountId("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accountId_should_reject_null() {
        assertThatThrownBy(() -> new AccountId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Amount ---

    @Test
    void amount_should_accept_positive_value() {
        Amount amount = new Amount(BigDecimal.TEN, Currency.getInstance("USD"));
        assertThat(amount.value()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(amount.currency()).isEqualTo(Currency.getInstance("USD"));
    }

    @Test
    void amount_should_reject_negative_value() {
        assertThatThrownBy(() -> new Amount(BigDecimal.valueOf(-1), Currency.getInstance("USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void amount_should_accept_zero() {
        Amount amount = new Amount(BigDecimal.ZERO, Currency.getInstance("GBP"));
        assertThat(amount.value()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- Transaction ---

    @Test
    void transaction_should_be_constructable_with_all_fields() {
        TransactionId id = TransactionId.generate();
        AccountId from = new AccountId("GB1234567890");
        AccountId to = new AccountId("US9876543210");
        Amount amount = new Amount(BigDecimal.valueOf(500), Currency.getInstance("USD"));
        Instant now = Instant.now();

        Transaction tx = new Transaction(id, amount, from, to, "Payment for services", now);

        assertThat(tx.id()).isEqualTo(id);
        assertThat(tx.amount()).isEqualTo(amount);
        assertThat(tx.fromAccount()).isEqualTo(from);
        assertThat(tx.toAccount()).isEqualTo(to);
        assertThat(tx.description()).isEqualTo("Payment for services");
        assertThat(tx.occurredAt()).isEqualTo(now);
    }

    // --- FraudDecision ---

    @Test
    void fraudDecision_should_accept_valid_values() {
        Instant now = Instant.now();
        FraudDecision decision = new FraudDecision(
                FraudDecision.Decision.APPROVE, BigDecimal.valueOf(25), "Low risk", now);

        assertThat(decision.decision()).isEqualTo(FraudDecision.Decision.APPROVE);
        assertThat(decision.riskScore()).isEqualByComparingTo(BigDecimal.valueOf(25));
        assertThat(decision.reasoning()).isEqualTo("Low risk");
        assertThat(decision.evaluatedAt()).isEqualTo(now);
    }

    @Test
    void fraudDecision_should_accept_boundary_risk_scores() {
        Instant now = Instant.now();
        assertThat(new FraudDecision(FraudDecision.Decision.APPROVE, BigDecimal.ZERO, "min", now)
                .riskScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(new FraudDecision(FraudDecision.Decision.BLOCK, BigDecimal.valueOf(100), "max", now)
                .riskScore()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void fraudDecision_should_reject_negative_risk_score() {
        assertThatThrownBy(() -> new FraudDecision(
                FraudDecision.Decision.APPROVE, BigDecimal.valueOf(-1), "bad", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fraudDecision_should_reject_risk_score_above_100() {
        assertThatThrownBy(() -> new FraudDecision(
                FraudDecision.Decision.BLOCK, BigDecimal.valueOf(101), "bad", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fraudDecision_should_reject_null_fields() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new FraudDecision(null, BigDecimal.TEN, "r", now))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FraudDecision(FraudDecision.Decision.FLAG, null, "r", now))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FraudDecision(FraudDecision.Decision.FLAG, BigDecimal.TEN, null, now))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FraudDecision(FraudDecision.Decision.FLAG, BigDecimal.TEN, "r", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void decision_enum_should_have_three_values() {
        assertThat(FraudDecision.Decision.values())
                .containsExactly(FraudDecision.Decision.APPROVE, FraudDecision.Decision.FLAG, FraudDecision.Decision.BLOCK);
    }
}
