package com.finstream.domain.model;

import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Positive;
import net.jqwik.api.constraints.BigRange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainPropertyTest {

    // --- AccountId ---

    @Property(tries = 200)
    void valid_account_ids_should_always_be_accepted(@ForAll("validAccountIds") String id) {
        assertThatNoException().isThrownBy(() -> new AccountId(id));
    }

    @Property(tries = 200)
    void invalid_account_ids_should_always_be_rejected(@ForAll("invalidAccountIds") String id) {
        assertThatThrownBy(() -> new AccountId(id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Provide
    Arbitrary<String> validAccountIds() {
        Arbitrary<String> prefix = Arbitraries.strings()
                .alpha().ofLength(2)
                .map(String::toUpperCase);
        Arbitrary<String> digits = Arbitraries.strings()
                .numeric().ofLength(10);
        return Combinators.combine(prefix, digits).as((p, d) -> p + d);
    }

    @Provide
    Arbitrary<String> invalidAccountIds() {
        return Arbitraries.oneOf(
                // lowercase prefix
                Arbitraries.strings().alpha().ofLength(2).map(s -> s.toLowerCase() + "1234567890"),
                // wrong digit count
                Arbitraries.strings().numeric().ofMinLength(1).ofMaxLength(9)
                        .map(d -> "AB" + d),
                // too many digits
                Arbitraries.strings().numeric().ofMinLength(11).ofMaxLength(15)
                        .map(d -> "AB" + d),
                // completely random short strings
                Arbitraries.strings().ofMinLength(1).ofMaxLength(5)
        );
    }

    // --- Amount ---

    @Property(tries = 200)
    void non_negative_amounts_should_always_be_accepted(
            @ForAll @BigRange(min = "0", max = "999999999") BigDecimal value) {
        assertThatNoException().isThrownBy(() -> new Amount(value, Currency.getInstance("USD")));
    }

    @Property(tries = 200)
    void negative_amounts_should_always_be_rejected(
            @ForAll @BigRange(min = "-999999999", max = "-0.01") BigDecimal value) {
        assertThatThrownBy(() -> new Amount(value, Currency.getInstance("USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- TransactionId ---

    @Property(tries = 100)
    void generated_transaction_ids_should_always_be_unique(@ForAll("batchSize") int size) {
        Set<TransactionId> ids = new HashSet<>();
        for (int i = 0; i < size; i++) {
            ids.add(TransactionId.generate());
        }
        assertThat(ids).hasSize(size);
    }

    @Provide
    Arbitrary<Integer> batchSize() {
        return Arbitraries.integers().between(2, 50);
    }

    // --- RuleGateResult ---

    @Property(tries = 200)
    void valid_rule_gate_results_should_always_be_constructable(
            @ForAll("ruleGateDecisions") RuleGateResult.Decision decision) {
        assertThatNoException().isThrownBy(() ->
                new RuleGateResult(decision, "Test reason", decision.defaultScore()));
    }

    @Property(tries = 100)
    void rule_gate_default_scores_should_be_non_negative(
            @ForAll("ruleGateDecisions") RuleGateResult.Decision decision) {
        assertThat(decision.defaultScore()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Provide
    Arbitrary<RuleGateResult.Decision> ruleGateDecisions() {
        return Arbitraries.of(RuleGateResult.Decision.values());
    }

    // --- LlmFraudAssessment ---

    @Property(tries = 200)
    void valid_llm_assessments_should_always_be_constructable(
            @ForAll @BigRange(min = "0", max = "100") BigDecimal riskScore) {
        assertThatNoException().isThrownBy(() ->
                new LlmFraudAssessment(riskScore, "Test reasoning"));
    }

    @Property(tries = 200)
    void out_of_range_llm_assessments_should_be_rejected(
            @ForAll @BigRange(min = "101", max = "999") BigDecimal riskScore) {
        assertThatThrownBy(() -> new LlmFraudAssessment(riskScore, "Bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- FraudDecision ---

    @Property(tries = 200)
    void valid_fraud_decisions_should_always_be_constructable(
            @ForAll @BigRange(min = "0", max = "100") BigDecimal riskScore,
            @ForAll("fraudDecisions") FraudDecision.Decision decision) {
        assertThatNoException().isThrownBy(() ->
                new FraudDecision(decision, riskScore, "Test", Instant.now()));
    }

    @Provide
    Arbitrary<FraudDecision.Decision> fraudDecisions() {
        return Arbitraries.of(FraudDecision.Decision.values());
    }
}
