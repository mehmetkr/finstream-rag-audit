package com.finstream.domain.service;

import com.finstream.domain.model.Amount;
import com.finstream.domain.model.RedactedTransaction;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.BigRange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class PiiRedactionPropertyTest {

    private final PiiRedactor redactor = new PiiRedactor();

    @Property(tries = 200)
    void redacted_output_should_never_contain_original_account_ids(
            @ForAll("transactions") Transaction transaction) {

        RedactedTransaction redacted = redactor.redact(transaction);

        String fromOriginal = transaction.fromAccount().value();
        String toOriginal = transaction.toAccount().value();

        assertThat(redacted.redactedFromAccount().value()).doesNotContain(fromOriginal);
        assertThat(redacted.redactedToAccount().value()).doesNotContain(toOriginal);
        assertThat(redacted.redactedDescription()).doesNotContain(fromOriginal);
        assertThat(redacted.redactedDescription()).doesNotContain(toOriginal);
    }

    @Property(tries = 200)
    void redacted_account_ids_should_always_be_8_char_lowercase_hex(
            @ForAll("transactions") Transaction transaction) {

        RedactedTransaction redacted = redactor.redact(transaction);

        assertThat(redacted.redactedFromAccount().value()).matches("^[0-9a-f]{8}$");
        assertThat(redacted.redactedToAccount().value()).matches("^[0-9a-f]{8}$");
    }

    @Property(tries = 200)
    void amount_should_always_be_preserved_exactly(
            @ForAll("transactions") Transaction transaction) {

        RedactedTransaction redacted = redactor.redact(transaction);

        assertThat(redacted.amount()).isEqualTo(transaction.amount());
    }

    @Property(tries = 200)
    void transaction_id_should_always_be_preserved(
            @ForAll("transactions") Transaction transaction) {

        RedactedTransaction redacted = redactor.redact(transaction);

        assertThat(redacted.id()).isEqualTo(transaction.id());
        assertThat(redacted.occurredAt()).isEqualTo(transaction.occurredAt());
    }

    @Provide
    Arbitrary<Transaction> transactions() {
        Arbitrary<AccountId> accountIds = validAccountIds().map(AccountId::new);
        Arbitrary<BigDecimal> amounts = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.valueOf(999_999));
        Arbitrary<String> descriptions = Arbitraries.oneOf(
                Arbitraries.of("Normal payment", "Wire transfer", "Monthly rent"),
                Arbitraries.of(
                        "Pay to user@example.com",
                        "SSN 123-45-6789 holder",
                        "Call 555-123-4567 to confirm"
                )
        );

        return Combinators.combine(accountIds, accountIds, amounts, descriptions)
                .as((from, to, amount, desc) -> new Transaction(
                        TransactionId.generate(),
                        new Amount(amount, Currency.getInstance("USD")),
                        from,
                        to,
                        desc,
                        Instant.now()
                ));
    }

    private static Arbitrary<String> validAccountIds() {
        Arbitrary<String> prefix = Arbitraries.strings()
                .alpha().ofLength(2)
                .map(String::toUpperCase);
        Arbitrary<String> digits = Arbitraries.strings()
                .numeric().ofLength(10);
        return Combinators.combine(prefix, digits).as((p, d) -> p + d);
    }
}
