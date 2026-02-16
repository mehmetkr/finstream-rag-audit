package com.finstream.infrastructure.adapters.embedding;

import com.finstream.domain.model.Amount;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTextRepresentationTest {

    @Test
    void should_format_transaction_as_text() {
        Transaction tx = new Transaction(
                TransactionId.generate(),
                new Amount(BigDecimal.valueOf(1500), Currency.getInstance("USD")),
                new AccountId("GB1234567890"),
                new AccountId("US9876543210"),
                "Monthly rent payment",
                Instant.parse("2026-02-15T10:00:00Z")
        );

        String text = PgVectorEmbeddingAdapter.toTextRepresentation(tx);

        assertThat(text).isEqualTo("1500 USD from GB1234567890 to US9876543210: Monthly rent payment");
    }

    @Test
    void should_use_plain_string_for_amount() {
        Transaction tx = new Transaction(
                TransactionId.generate(),
                new Amount(new BigDecimal("49999.9900"), Currency.getInstance("EUR")),
                new AccountId("DE1234567890"),
                new AccountId("FR9876543210"),
                "Wire transfer",
                Instant.now()
        );

        String text = PgVectorEmbeddingAdapter.toTextRepresentation(tx);

        assertThat(text).startsWith("49999.9900 EUR from DE1234567890");
    }
}
