package com.finstream.domain.service;

import com.finstream.domain.model.RedactedAccountId;
import com.finstream.domain.model.RedactedTransaction;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Irreversibly redacts PII from transactions before external processing.
 * Account IDs are replaced with SHA-256 hash prefixes; description text
 * is scrubbed of email, phone, and SSN patterns.
 */
public class PiiRedactor {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(\\+?\\d{1,3}[\\-.\\s]?)?\\(?\\d{3}\\)?[\\-.\\s]?\\d{3}[\\-.\\s]?\\d{4}");

    private static final Pattern SSN_PATTERN =
            Pattern.compile("\\d{3}-\\d{2}-\\d{4}");

    private static final int HASH_PREFIX_LENGTH = 8;

    public RedactedTransaction redact(Transaction transaction) {
        return new RedactedTransaction(
                transaction.id(),
                transaction.amount(),
                redactAccountId(transaction.fromAccount()),
                redactAccountId(transaction.toAccount()),
                scrubDescription(transaction.description()),
                transaction.occurredAt()
        );
    }

    RedactedAccountId redactAccountId(AccountId accountId) {
        byte[] hash = sha256(accountId.value());
        String hexPrefix = HexFormat.of().formatHex(hash, 0, HASH_PREFIX_LENGTH / 2);
        return new RedactedAccountId(hexPrefix);
    }

    String scrubDescription(String description) {
        String result = SSN_PATTERN.matcher(description).replaceAll("[REDACTED_SSN]");
        result = EMAIL_PATTERN.matcher(result).replaceAll("[REDACTED_EMAIL]");
        result = PHONE_PATTERN.matcher(result).replaceAll("[REDACTED_PHONE]");
        return result;
    }

    private static byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available in every JVM", e);
        }
    }
}
