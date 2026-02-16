package com.finstream.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "finstream.fraud")
public record FraudEvaluationProperties(
        BigDecimal amountThreshold,
        int velocityLimit,
        Duration velocityWindow,
        int ragMaxResults,
        int historyLimit,
        Weights weights,
        Thresholds thresholds
) {
    public record Weights(double ruleGate, double rag, double llm) {}

    public record Thresholds(int approveMax, int flagMax) {}
}
