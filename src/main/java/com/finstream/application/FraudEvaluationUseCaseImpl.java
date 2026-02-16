package com.finstream.application;

import com.finstream.domain.model.FraudDecision;
import com.finstream.domain.model.FraudDecision.Decision;
import com.finstream.domain.model.FraudEvaluationConfig;
import com.finstream.domain.model.LlmFraudAssessment;
import com.finstream.domain.model.RuleGateResult;
import com.finstream.domain.model.ScoredTransaction;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.ports.inbound.FraudEvaluationUseCase;
import com.finstream.domain.ports.outbound.EmbeddingStorePort;
import com.finstream.domain.ports.outbound.LlmFraudAnalysisPort;
import com.finstream.domain.ports.outbound.UserHistoryPort;
import com.finstream.domain.service.RuleGateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FraudEvaluationUseCaseImpl implements FraudEvaluationUseCase {

    private static final Logger log = LoggerFactory.getLogger(FraudEvaluationUseCaseImpl.class);

    private final RuleGateService ruleGateService;
    private final UserHistoryPort userHistoryPort;
    private final EmbeddingStorePort embeddingStorePort;
    private final LlmFraudAnalysisPort llmFraudAnalysisPort;
    private final FraudEvaluationConfig config;

    public FraudEvaluationUseCaseImpl(RuleGateService ruleGateService,
                                       UserHistoryPort userHistoryPort,
                                       EmbeddingStorePort embeddingStorePort,
                                       LlmFraudAnalysisPort llmFraudAnalysisPort,
                                       FraudEvaluationConfig config) {
        this.ruleGateService = ruleGateService;
        this.userHistoryPort = userHistoryPort;
        this.embeddingStorePort = embeddingStorePort;
        this.llmFraudAnalysisPort = llmFraudAnalysisPort;
        this.config = config;
    }

    @Override
    public FraudDecision evaluate(Transaction transaction) {
        // Phase 1: synchronous rule gate
        Instant velocitySince = Instant.now().minus(config.velocityWindow());
        int recentCount = userHistoryPort.countRecentTransactions(
                transaction.fromAccount(), velocitySince);
        RuleGateResult gateResult = ruleGateService.evaluate(transaction, recentCount);

        log.info("Rule gate for {}: {} — {}",
                transaction.id().value(), gateResult.decision(), gateResult.reason());

        // Early return for definitive decisions
        return switch (gateResult.decision()) {
            case APPROVE -> toDecision(gateResult, "Rule gate: approved");
            case BLOCK -> toDecision(gateResult, "Rule gate: " + gateResult.reason());
            case FLAG -> evaluatePhaseTwo(transaction, gateResult);
        };
    }

    /**
     * Phase 2: scatter-gather with virtual threads.
     * Parallel fans: RAG similarity + user history.
     * RAG result feeds into LLM analysis via thenComposeAsync.
     * Graceful degradation: failures degrade to Optional.empty().
     */
    private FraudDecision evaluatePhaseTwo(Transaction transaction, RuleGateResult gateResult) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // Fan-out: RAG similarity search
            CompletableFuture<Optional<List<ScoredTransaction>>> ragFuture =
                    CompletableFuture.supplyAsync(
                            () -> embeddingStorePort.findSimilar(transaction, config.ragMaxResults()),
                            executor
                    ).handle((result, ex) -> {
                        if (ex != null) {
                            log.warn("RAG lookup failed for {}: {}", transaction.id().value(), ex.getMessage());
                            return Optional.empty();
                        }
                        return Optional.of(result);
                    });

            // Fan-out: user history
            CompletableFuture<Optional<List<Transaction>>> historyFuture =
                    CompletableFuture.supplyAsync(
                            () -> userHistoryPort.findRecentTransactions(
                                    transaction.fromAccount(), config.historyLimit()),
                            executor
                    ).handle((result, ex) -> {
                        if (ex != null) {
                            log.warn("History lookup failed for {}: {}", transaction.id().value(), ex.getMessage());
                            return Optional.empty();
                        }
                        return Optional.of(result);
                    });

            // Chain: RAG → LLM (thenComposeAsync for dependent async step)
            CompletableFuture<Optional<LlmFraudAssessment>> llmFuture =
                    ragFuture.thenComposeAsync(ragOpt -> {
                        List<ScoredTransaction> similar = ragOpt.orElse(List.of());
                        return CompletableFuture.supplyAsync(
                                () -> llmFraudAnalysisPort.analyze(transaction, similar),
                                executor
                        );
                    }, executor).handle((result, ex) -> {
                        if (ex != null) {
                            log.warn("LLM analysis failed for {}: {}", transaction.id().value(), ex.getMessage());
                            return Optional.empty();
                        }
                        return Optional.of(result);
                    });

            // Combine: wait for history + LLM, then aggregate
            return llmFuture.thenCombine(historyFuture, (llmOpt, historyOpt) ->
                    aggregateScores(transaction, gateResult, llmOpt, historyOpt)
            ).join();
        }
    }

    private FraudDecision aggregateScores(Transaction transaction,
                                           RuleGateResult gateResult,
                                           Optional<LlmFraudAssessment> llmOpt,
                                           Optional<List<Transaction>> historyOpt) {
        // Rule gate contribution (always present)
        BigDecimal ruleComponent = gateResult.baselineRiskScore()
                .multiply(BigDecimal.valueOf(config.ruleGateWeight()));

        // RAG/LLM contribution (may be absent due to graceful degradation)
        BigDecimal llmComponent = llmOpt
                .map(assessment -> assessment.riskScore().multiply(BigDecimal.valueOf(config.llmWeight())))
                .orElse(BigDecimal.ZERO);

        // History-based heuristic: more history context → redistribute weight
        BigDecimal ragComponent = historyOpt
                .map(history -> computeHistoryRiskContribution(history, transaction))
                .map(score -> score.multiply(BigDecimal.valueOf(config.ragWeight())))
                .orElse(BigDecimal.ZERO);

        // If LLM or RAG failed, redistribute their weight to rule gate
        double effectiveTotal = config.ruleGateWeight()
                + (llmOpt.isPresent() ? config.llmWeight() : 0.0)
                + (historyOpt.isPresent() ? config.ragWeight() : 0.0);

        BigDecimal weightedScore = ruleComponent.add(llmComponent).add(ragComponent)
                .divide(BigDecimal.valueOf(effectiveTotal), 2, RoundingMode.HALF_UP);

        BigDecimal clampedScore = weightedScore.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));

        Decision decision = classifyScore(clampedScore);
        String reasoning = buildReasoning(gateResult, llmOpt, historyOpt, clampedScore);

        log.info("Aggregated score for {}: {} → {}", transaction.id().value(), clampedScore, decision);
        return new FraudDecision(decision, clampedScore, reasoning, Instant.now());
    }

    private BigDecimal computeHistoryRiskContribution(List<Transaction> history, Transaction current) {
        if (history.isEmpty()) {
            return BigDecimal.valueOf(60); // no history → moderate risk
        }
        // Heuristic: average amount ratio as risk indicator
        double avgHistoryAmount = history.stream()
                .mapToDouble(tx -> tx.amount().value().doubleValue())
                .average()
                .orElse(0.0);
        if (avgHistoryAmount == 0.0) {
            return BigDecimal.valueOf(50);
        }
        double ratio = current.amount().value().doubleValue() / avgHistoryAmount;
        // ratio > 3x average → high risk; < 1x → low risk
        double score = Math.min(100, Math.max(0, (ratio - 1.0) * 30.0 + 30.0));
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private Decision classifyScore(BigDecimal score) {
        int intScore = score.intValue();
        if (intScore < config.approveMaxScore()) {
            return Decision.APPROVE;
        } else if (intScore < config.flagMaxScore()) {
            return Decision.FLAG;
        } else {
            return Decision.BLOCK;
        }
    }

    private String buildReasoning(RuleGateResult gateResult,
                                   Optional<LlmFraudAssessment> llmOpt,
                                   Optional<List<Transaction>> historyOpt,
                                   BigDecimal score) {
        var sb = new StringBuilder();
        sb.append("Rule gate: ").append(gateResult.reason());
        llmOpt.ifPresent(llm ->
                sb.append(" | LLM: ").append(llm.reasoning()));
        historyOpt.ifPresent(history ->
                sb.append(" | History: ").append(history.size()).append(" recent transactions"));
        sb.append(" | Weighted score: ").append(score.toPlainString());
        return sb.toString();
    }

    private static FraudDecision toDecision(RuleGateResult gateResult, String reasoning) {
        Decision decision = switch (gateResult.decision()) {
            case APPROVE -> Decision.APPROVE;
            case FLAG -> Decision.FLAG;
            case BLOCK -> Decision.BLOCK;
        };
        return new FraudDecision(decision, gateResult.baselineRiskScore(), reasoning, Instant.now());
    }
}
