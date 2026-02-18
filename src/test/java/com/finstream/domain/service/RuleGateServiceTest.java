package com.finstream.domain.service;

import com.finstream.domain.model.Amount;
import com.finstream.domain.model.RuleGateResult;
import com.finstream.domain.model.RuleGateResult.Decision;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleGateServiceTest {

    private static final BigDecimal AMOUNT_THRESHOLD = BigDecimal.valueOf(10_000);
    private static final int VELOCITY_LIMIT = 10;

    private RuleGateService service;

    @BeforeEach
    void setUp() {
        service = new RuleGateService(AMOUNT_THRESHOLD, VELOCITY_LIMIT);
    }

    @Test
    void should_approve_normal_transaction() {
        Transaction tx = transaction(BigDecimal.valueOf(500), "Regular payment");
        RuleGateResult result = service.evaluate(tx, 2);

        assertThat(result.decision()).isEqualTo(Decision.APPROVE);
        assertThat(result.baselineRiskScore()).isEqualByComparingTo(Decision.APPROVE.defaultScore());
    }

    @Test
    void should_block_sanctioned_pattern_money_launder() {
        Transaction tx = transaction(BigDecimal.valueOf(100), "Money laundering scheme");
        RuleGateResult result = service.evaluate(tx, 0);

        assertThat(result.decision()).isEqualTo(Decision.BLOCK);
        assertThat(result.reason()).contains("Sanctioned pattern");
    }

    @Test
    void should_block_sanctioned_pattern_terrorist() {
        Transaction tx = transaction(BigDecimal.valueOf(100), "Terrorist financing");
        RuleGateResult result = service.evaluate(tx, 0);

        assertThat(result.decision()).isEqualTo(Decision.BLOCK);
    }

    @Test
    void should_block_sanctioned_pattern_case_insensitive() {
        Transaction tx = transaction(BigDecimal.valueOf(100), "SANCTIONS VIOLATION detected");
        RuleGateResult result = service.evaluate(tx, 0);

        assertThat(result.decision()).isEqualTo(Decision.BLOCK);
    }

    @Test
    void should_block_sanctioned_pattern_illicit_fund() {
        Transaction tx = transaction(BigDecimal.valueOf(100), "Transfer of illicit funds");
        RuleGateResult result = service.evaluate(tx, 0);

        assertThat(result.decision()).isEqualTo(Decision.BLOCK);
    }

    @Test
    void should_flag_high_velocity() {
        Transaction tx = transaction(BigDecimal.valueOf(500), "Normal payment");
        RuleGateResult result = service.evaluate(tx, 15);

        assertThat(result.decision()).isEqualTo(Decision.FLAG);
        assertThat(result.reason()).contains("High velocity");
        assertThat(result.reason()).contains("15");
    }

    @Test
    void should_flag_high_amount() {
        Transaction tx = transaction(BigDecimal.valueOf(15_000), "Large transfer");
        RuleGateResult result = service.evaluate(tx, 2);

        assertThat(result.decision()).isEqualTo(Decision.FLAG);
        assertThat(result.reason()).contains("High amount");
    }

    @Test
    void should_prioritize_block_over_flag_when_sanctioned_and_high_amount() {
        Transaction tx = transaction(BigDecimal.valueOf(50_000), "Money launder operation");
        RuleGateResult result = service.evaluate(tx, 20);

        assertThat(result.decision()).isEqualTo(Decision.BLOCK);
    }

    @Test
    void should_prioritize_velocity_over_amount_when_both_flagged() {
        Transaction tx = transaction(BigDecimal.valueOf(15_000), "Normal payment");
        RuleGateResult result = service.evaluate(tx, 15);

        // Velocity is checked before amount
        assertThat(result.decision()).isEqualTo(Decision.FLAG);
        assertThat(result.reason()).contains("velocity");
    }

    @Test
    void should_approve_at_exact_threshold_amount() {
        Transaction tx = transaction(AMOUNT_THRESHOLD, "Threshold payment");
        RuleGateResult result = service.evaluate(tx, 2);

        assertThat(result.decision()).isEqualTo(Decision.APPROVE);
    }

    @Test
    void should_approve_at_exact_velocity_limit() {
        Transaction tx = transaction(BigDecimal.valueOf(500), "Normal payment");
        RuleGateResult result = service.evaluate(tx, VELOCITY_LIMIT);

        assertThat(result.decision()).isEqualTo(Decision.APPROVE);
    }

    @Test
    void should_reject_null_description_at_construction() {
        assertThatThrownBy(() -> new Transaction(
                TransactionId.generate(),
                new Amount(BigDecimal.valueOf(500), Currency.getInstance("USD")),
                new AccountId("GB1234567890"),
                new AccountId("US9876543210"),
                null,
                Instant.now()
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Description cannot be null");
    }

    private static Transaction transaction(BigDecimal amount, String description) {
        return new Transaction(
                TransactionId.generate(),
                new Amount(amount, Currency.getInstance("USD")),
                new AccountId("GB1234567890"),
                new AccountId("US9876543210"),
                description,
                Instant.now()
        );
    }
}
