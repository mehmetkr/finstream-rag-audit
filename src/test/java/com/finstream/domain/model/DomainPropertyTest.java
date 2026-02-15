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
}
