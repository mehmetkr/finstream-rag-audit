package com.finstream.domain.service;

import com.finstream.domain.model.RuleGateResult;
import com.finstream.domain.model.RuleGateResult.Decision;
import com.finstream.domain.model.Transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

public class RuleGateService {

    private static final List<Pattern> SANCTIONED_PATTERNS = List.of(
            Pattern.compile("(?i)money\\s*launder"),
            Pattern.compile("(?i)terrorist"),
            Pattern.compile("(?i)sanctions?\\s*violation"),
            Pattern.compile("(?i)illicit\\s*fund")
    );

    private final BigDecimal amountThreshold;
    private final int velocityLimit;

    public RuleGateService(BigDecimal amountThreshold, int velocityLimit) {
        this.amountThreshold = amountThreshold;
        this.velocityLimit = velocityLimit;
    }

    public RuleGateResult evaluate(Transaction transaction, int recentTransactionCount) {
        // Check sanctioned patterns first (highest priority)
        for (Pattern pattern : SANCTIONED_PATTERNS) {
            if (pattern.matcher(transaction.description()).find()) {
                return new RuleGateResult(Decision.BLOCK,
                        "Sanctioned pattern detected: " + pattern.pattern(),
                        Decision.BLOCK.defaultScore());
            }
        }

        // Check velocity
        if (recentTransactionCount > velocityLimit) {
            return new RuleGateResult(Decision.FLAG,
                    "High velocity: %d transactions exceed limit of %d".formatted(
                            recentTransactionCount, velocityLimit),
                    Decision.FLAG.defaultScore());
        }

        // Check amount threshold
        if (transaction.amount().value().compareTo(amountThreshold) > 0) {
            return new RuleGateResult(Decision.FLAG,
                    "High amount: %s exceeds threshold of %s".formatted(
                            transaction.amount().value().toPlainString(),
                            amountThreshold.toPlainString()),
                    Decision.FLAG.defaultScore());
        }

        return new RuleGateResult(Decision.APPROVE,
                "No risk indicators detected",
                Decision.APPROVE.defaultScore());
    }
}
