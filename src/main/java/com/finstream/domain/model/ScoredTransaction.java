package com.finstream.domain.model;

import java.util.Objects;

public record ScoredTransaction(Transaction transaction, double similarityScore) {

    public ScoredTransaction {
        Objects.requireNonNull(transaction, "Transaction cannot be null");
        if (similarityScore < 0.0 || similarityScore > 1.0) {
            throw new IllegalArgumentException(
                    "Similarity score must be between 0.0 and 1.0, got: " + similarityScore);
        }
    }
}
