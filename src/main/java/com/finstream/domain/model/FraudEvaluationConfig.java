package com.finstream.domain.model;

import java.time.Duration;

public record FraudEvaluationConfig(
        Duration velocityWindow,
        int ragMaxResults,
        int historyLimit,
        double ruleGateWeight,
        double ragWeight,
        double llmWeight,
        int approveMaxScore,
        int flagMaxScore
) {}
