package com.finstream.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record LlmFraudAssessment(BigDecimal riskScore, String reasoning) {

    public LlmFraudAssessment {
        Objects.requireNonNull(riskScore, "Risk score cannot be null");
        Objects.requireNonNull(reasoning, "Reasoning cannot be null");
        if (riskScore.compareTo(BigDecimal.ZERO) < 0 || riskScore.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100, got: " + riskScore);
        }
    }
}
