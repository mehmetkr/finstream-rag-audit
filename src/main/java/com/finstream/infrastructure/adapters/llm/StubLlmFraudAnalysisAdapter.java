package com.finstream.infrastructure.adapters.llm;

import com.finstream.domain.model.LlmFraudAssessment;
import com.finstream.domain.model.ScoredTransaction;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.ports.outbound.LlmFraudAnalysisPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class StubLlmFraudAnalysisAdapter implements LlmFraudAnalysisPort {

    private static final Logger log = LoggerFactory.getLogger(StubLlmFraudAnalysisAdapter.class);
    private static final BigDecimal AMOUNT_SCALE_FACTOR = BigDecimal.valueOf(200);
    private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(100);

    @Override
    public LlmFraudAssessment analyze(Transaction transaction,
                                       List<ScoredTransaction> similarTransactions) {
        BigDecimal amountScore = computeAmountScore(transaction);
        BigDecimal similarityAdjustment = computeSimilarityAdjustment(similarTransactions);
        BigDecimal finalScore = amountScore.add(similarityAdjustment)
                .max(BigDecimal.ZERO)
                .min(MAX_SCORE)
                .setScale(2, RoundingMode.HALF_UP);

        String reasoning = "[Stub] Heuristic: amount-based=%.1f, similarity-adj=%.1f, similar-count=%d".formatted(
                amountScore, similarityAdjustment, similarTransactions.size());

        log.debug("Stub LLM analysis for {}: {}", transaction.id().value(), reasoning);
        return new LlmFraudAssessment(finalScore, reasoning);
    }

    private static BigDecimal computeAmountScore(Transaction transaction) {
        // Scale linearly: 200 currency units ≈ 1.0 risk score, capped at 100
        return transaction.amount().value()
                .divide(AMOUNT_SCALE_FACTOR, 2, RoundingMode.HALF_UP)
                .min(MAX_SCORE);
    }

    private static BigDecimal computeSimilarityAdjustment(List<ScoredTransaction> similar) {
        if (similar.isEmpty()) {
            return BigDecimal.TEN; // unknown pattern is slightly riskier
        }
        double avgSimilarity = similar.stream()
                .mapToDouble(ScoredTransaction::similarityScore)
                .average()
                .orElse(0.0);
        // High similarity to existing transactions → lower risk (known pattern)
        return BigDecimal.valueOf(-20.0 * avgSimilarity).setScale(2, RoundingMode.HALF_UP);
    }
}
