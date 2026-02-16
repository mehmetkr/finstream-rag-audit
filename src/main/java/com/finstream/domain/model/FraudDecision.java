package com.finstream.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record FraudDecision(
        Decision decision,
        BigDecimal riskScore,
        String reasoning,
        Instant evaluatedAt
) {
    public FraudDecision {
        Objects.requireNonNull(decision, "Decision cannot be null");
        Objects.requireNonNull(riskScore, "Risk score cannot be null");
        Objects.requireNonNull(reasoning, "Reasoning cannot be null");
        Objects.requireNonNull(evaluatedAt, "EvaluatedAt cannot be null");
        if (riskScore.compareTo(BigDecimal.ZERO) < 0 || riskScore.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100, got: " + riskScore);
        }
    }

    public enum Decision {
        APPROVE, FLAG, BLOCK
    }
}
