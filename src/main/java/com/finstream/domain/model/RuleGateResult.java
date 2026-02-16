package com.finstream.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record RuleGateResult(Decision decision, String reason, BigDecimal baselineRiskScore) {

    public RuleGateResult {
        Objects.requireNonNull(decision, "Decision cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
        Objects.requireNonNull(baselineRiskScore, "Baseline risk score cannot be null");
    }

    public enum Decision {
        APPROVE(BigDecimal.TEN),
        FLAG(BigDecimal.valueOf(50)),
        BLOCK(BigDecimal.valueOf(95));

        private final BigDecimal defaultScore;

        Decision(BigDecimal defaultScore) {
            this.defaultScore = defaultScore;
        }

        public BigDecimal defaultScore() {
            return defaultScore;
        }
    }
}
