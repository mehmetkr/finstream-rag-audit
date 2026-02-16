package com.finstream.domain.service;

import com.finstream.domain.model.Amount;
import com.finstream.domain.model.RedactedTransaction;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class PiiRedactorTest {

    private final PiiRedactor redactor = new PiiRedactor();

    @Test
    void should_hash_account_ids_to_8_char_hex() {
        Transaction tx = transaction("Normal payment");

        RedactedTransaction redacted = redactor.redact(tx);

        assertThat(redacted.redactedFromAccount().value()).matches("^[0-9a-f]{8}$");
        assertThat(redacted.redactedToAccount().value()).matches("^[0-9a-f]{8}$");
    }

    @Test
    void should_produce_deterministic_hashes() {
        Transaction tx1 = transaction("Payment 1");
        Transaction tx2 = transaction("Payment 2");

        String hash1 = redactor.redact(tx1).redactedFromAccount().value();
        String hash2 = redactor.redact(tx2).redactedFromAccount().value();

        // Same account ID → same hash
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void should_produce_different_hashes_for_different_accounts() {
        AccountId from = new AccountId("GB1234567890");
        AccountId to = new AccountId("US9876543210");

        String fromHash = redactor.redactAccountId(from).value();
        String toHash = redactor.redactAccountId(to).value();

        assertThat(fromHash).isNotEqualTo(toHash);
    }

    @Test
    void hashed_value_should_not_contain_original_account_id() {
        AccountId account = new AccountId("GB1234567890");

        String hash = redactor.redactAccountId(account).value();

        assertThat(hash).doesNotContain("GB1234567890");
        assertThat(hash).doesNotContain("GB");
        assertThat(hash).doesNotContain("1234567890");
    }

    @Test
    void should_redact_email_in_description() {
        Transaction tx = transaction("Payment to john.doe@example.com for services");

        RedactedTransaction redacted = redactor.redact(tx);

        assertThat(redacted.redactedDescription())
                .isEqualTo("Payment to [REDACTED_EMAIL] for services");
        assertThat(redacted.redactedDescription()).doesNotContain("john.doe@example.com");
    }

    @Test
    void should_redact_phone_in_description() {
        Transaction tx = transaction("Contact: (555) 123-4567");

        RedactedTransaction redacted = redactor.redact(tx);

        assertThat(redacted.redactedDescription()).contains("[REDACTED_PHONE]");
        assertThat(redacted.redactedDescription()).doesNotContain("555");
    }

    @Test
    void should_redact_ssn_in_description() {
        Transaction tx = transaction("SSN: 123-45-6789 on file");

        RedactedTransaction redacted = redactor.redact(tx);

        assertThat(redacted.redactedDescription())
                .isEqualTo("SSN: [REDACTED_SSN] on file");
        assertThat(redacted.redactedDescription()).doesNotContain("123-45-6789");
    }

    @Test
    void should_redact_multiple_pii_patterns() {
        Transaction tx = transaction(
                "From john@test.com, SSN 123-45-6789, call 555-123-4567");

        RedactedTransaction redacted = redactor.redact(tx);

        assertThat(redacted.redactedDescription())
                .contains("[REDACTED_EMAIL]")
                .contains("[REDACTED_SSN]")
                .contains("[REDACTED_PHONE]");
        assertThat(redacted.redactedDescription())
                .doesNotContain("john@test.com")
                .doesNotContain("123-45-6789")
                .doesNotContain("555-123-4567");
    }

    @Test
    void should_leave_clean_description_unchanged() {
        Transaction tx = transaction("Normal payment for groceries");

        RedactedTransaction redacted = redactor.redact(tx);

        assertThat(redacted.redactedDescription()).isEqualTo("Normal payment for groceries");
    }

    @Test
    void should_preserve_amount_and_currency() {
        Transaction tx = transaction("Payment");

        RedactedTransaction redacted = redactor.redact(tx);

        assertThat(redacted.amount()).isEqualTo(tx.amount());
    }

    @Test
    void should_preserve_transaction_id_and_timestamp() {
        Transaction tx = transaction("Payment");

        RedactedTransaction redacted = redactor.redact(tx);

        assertThat(redacted.id()).isEqualTo(tx.id());
        assertThat(redacted.occurredAt()).isEqualTo(tx.occurredAt());
    }

    private static Transaction transaction(String description) {
        return new Transaction(
                TransactionId.generate(),
                new Amount(BigDecimal.valueOf(1500), Currency.getInstance("USD")),
                new AccountId("GB1234567890"),
                new AccountId("US9876543210"),
                description,
                Instant.now()
        );
    }
}
