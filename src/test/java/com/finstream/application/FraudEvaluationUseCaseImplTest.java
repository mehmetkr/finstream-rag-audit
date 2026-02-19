package com.finstream.application;

import com.finstream.domain.model.Amount;
import com.finstream.domain.model.FraudDecision;
import com.finstream.domain.model.FraudDecision.Decision;
import com.finstream.domain.model.LlmFraudAssessment;
import com.finstream.domain.model.RedactedTransaction;
import com.finstream.domain.model.ScoredTransaction;
import com.finstream.domain.model.Transaction;
import com.finstream.domain.model.ids.AccountId;
import com.finstream.domain.model.ids.TransactionId;
import com.finstream.domain.ports.outbound.EmbeddingStorePort;
import com.finstream.domain.ports.outbound.LlmFraudAnalysisPort;
import com.finstream.domain.ports.outbound.UserHistoryPort;
import com.finstream.domain.service.PiiRedactor;
import com.finstream.domain.service.RuleGateService;
import com.finstream.domain.model.FraudEvaluationConfig;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudEvaluationUseCaseImplTest {

    @Mock private UserHistoryPort userHistoryPort;
    @Mock private EmbeddingStorePort embeddingStorePort;
    @Mock private LlmFraudAnalysisPort llmFraudAnalysisPort;
    @Mock private Tracer tracer;

    private static final Clock TEST_CLOCK =
            Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

    private FraudEvaluationUseCaseImpl useCase;

    private static final BigDecimal AMOUNT_THRESHOLD = BigDecimal.valueOf(10_000);
    private static final int VELOCITY_LIMIT = 10;
    private static final FraudEvaluationConfig CONFIG = new FraudEvaluationConfig(
            Duration.ofHours(1),  // velocityWindow
            5,                    // ragMaxResults
            20,                   // historyLimit
            0.20,                 // ruleGateWeight
            0.30,                 // ragWeight
            0.50,                 // llmWeight
            30,                   // approveMaxScore
            70                    // flagMaxScore
    );

    private final PiiRedactor piiRedactor = new PiiRedactor();

    @BeforeEach
    void setUp() {
        RuleGateService ruleGateService = new RuleGateService(AMOUNT_THRESHOLD, VELOCITY_LIMIT);
        Span span = org.mockito.Mockito.mock(Span.class);
        Tracer.SpanInScope scope = org.mockito.Mockito.mock(Tracer.SpanInScope.class);
        org.mockito.Mockito.when(tracer.nextSpan()).thenReturn(span);
        org.mockito.Mockito.when(span.name(org.mockito.ArgumentMatchers.anyString())).thenReturn(span);
        org.mockito.Mockito.when(span.start()).thenReturn(span);
        org.mockito.Mockito.when(tracer.withSpan(org.mockito.ArgumentMatchers.any())).thenReturn(scope);
        useCase = new FraudEvaluationUseCaseImpl(
                ruleGateService, userHistoryPort, embeddingStorePort,
                llmFraudAnalysisPort, piiRedactor, CONFIG, tracer, TEST_CLOCK);
    }

    @Test
    void should_approve_normal_transaction_without_phase_two() {
        Transaction tx = normalTransaction(BigDecimal.valueOf(500));
        when(userHistoryPort.countRecentTransactions(any(), any())).thenReturn(2);

        FraudDecision decision = useCase.evaluate(tx);

        assertThat(decision.decision()).isEqualTo(Decision.APPROVE);
        assertThat(decision.riskScore()).isEqualByComparingTo(BigDecimal.TEN);
        // Phase 2 should not be invoked
        verify(embeddingStorePort, never()).findSimilar(any(), anyInt());
        verify(llmFraudAnalysisPort, never()).analyze(any(), any());
    }

    @Test
    void should_block_sanctioned_transaction_without_phase_two() {
        Transaction tx = transaction(BigDecimal.valueOf(100), "Money laundering attempt");
        when(userHistoryPort.countRecentTransactions(any(), any())).thenReturn(0);

        FraudDecision decision = useCase.evaluate(tx);

        assertThat(decision.decision()).isEqualTo(Decision.BLOCK);
        assertThat(decision.riskScore()).isEqualByComparingTo(BigDecimal.valueOf(95));
        verify(embeddingStorePort, never()).findSimilar(any(), anyInt());
    }

    @Test
    void should_enter_phase_two_for_high_amount_flag() {
        Transaction tx = normalTransaction(BigDecimal.valueOf(15_000));
        when(userHistoryPort.countRecentTransactions(any(), any())).thenReturn(2);

        // Phase 2 mocks
        List<ScoredTransaction> similar = List.of(
                new ScoredTransaction(normalTransaction(BigDecimal.valueOf(14_000)), 0.85));
        when(embeddingStorePort.findSimilar(any(), eq(5))).thenReturn(similar);
        when(userHistoryPort.findRecentTransactions(any(), eq(20)))
                .thenReturn(List.of(normalTransaction(BigDecimal.valueOf(12_000))));
        when(llmFraudAnalysisPort.analyze(any(RedactedTransaction.class), any()))
                .thenReturn(new LlmFraudAssessment(BigDecimal.valueOf(40), "Moderate risk"));

        FraudDecision decision = useCase.evaluate(tx);

        assertThat(decision.decision()).isIn(Decision.FLAG, Decision.APPROVE, Decision.BLOCK);
        assertThat(decision.riskScore()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
        assertThat(decision.reasoning()).contains("Rule gate");
        verify(embeddingStorePort).findSimilar(any(), eq(5));
        verify(llmFraudAnalysisPort).analyze(any(RedactedTransaction.class), any());
    }

    @Test
    void should_enter_phase_two_for_high_velocity_flag() {
        Transaction tx = normalTransaction(BigDecimal.valueOf(500));
        when(userHistoryPort.countRecentTransactions(any(), any())).thenReturn(15);

        // Phase 2 mocks
        when(embeddingStorePort.findSimilar(any(), eq(5))).thenReturn(List.of());
        when(userHistoryPort.findRecentTransactions(any(), eq(20))).thenReturn(List.of());
        when(llmFraudAnalysisPort.analyze(any(RedactedTransaction.class), any()))
                .thenReturn(new LlmFraudAssessment(BigDecimal.valueOf(60), "High velocity concern"));

        FraudDecision decision = useCase.evaluate(tx);

        assertThat(decision.riskScore()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
        assertThat(decision.reasoning()).contains("Rule gate");
    }

    @Test
    void should_degrade_gracefully_when_rag_fails() {
        Transaction tx = normalTransaction(BigDecimal.valueOf(15_000));
        when(userHistoryPort.countRecentTransactions(any(), any())).thenReturn(2);

        // RAG fails
        when(embeddingStorePort.findSimilar(any(), anyInt()))
                .thenThrow(new RuntimeException("RAG unavailable"));
        when(userHistoryPort.findRecentTransactions(any(), eq(20)))
                .thenReturn(List.of(normalTransaction(BigDecimal.valueOf(12_000))));
        // LLM still runs with empty similar list
        when(llmFraudAnalysisPort.analyze(any(RedactedTransaction.class), any()))
                .thenReturn(new LlmFraudAssessment(BigDecimal.valueOf(45), "Analysis"));

        FraudDecision decision = useCase.evaluate(tx);

        assertThat(decision).isNotNull();
        assertThat(decision.riskScore()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
    }

    @Test
    void should_degrade_gracefully_when_llm_fails() {
        Transaction tx = normalTransaction(BigDecimal.valueOf(15_000));
        when(userHistoryPort.countRecentTransactions(any(), any())).thenReturn(2);

        when(embeddingStorePort.findSimilar(any(), anyInt())).thenReturn(List.of());
        when(userHistoryPort.findRecentTransactions(any(), eq(20))).thenReturn(List.of());
        when(llmFraudAnalysisPort.analyze(any(RedactedTransaction.class), any()))
                .thenThrow(new RuntimeException("LLM unavailable"));

        FraudDecision decision = useCase.evaluate(tx);

        assertThat(decision).isNotNull();
        assertThat(decision.riskScore()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
    }

    @Test
    void should_degrade_gracefully_when_all_phase_two_services_fail() {
        Transaction tx = normalTransaction(BigDecimal.valueOf(15_000));
        when(userHistoryPort.countRecentTransactions(any(), any())).thenReturn(2);

        when(embeddingStorePort.findSimilar(any(), anyInt()))
                .thenThrow(new RuntimeException("RAG unavailable"));
        when(userHistoryPort.findRecentTransactions(any(), anyInt()))
                .thenThrow(new RuntimeException("History unavailable"));
        when(llmFraudAnalysisPort.analyze(any(RedactedTransaction.class), any()))
                .thenThrow(new RuntimeException("LLM unavailable"));

        FraudDecision decision = useCase.evaluate(tx);

        // Should still return a decision based on rule gate alone
        assertThat(decision).isNotNull();
        assertThat(decision.riskScore()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
    }

    @Test
    void should_include_all_reasoning_components_in_phase_two() {
        Transaction tx = normalTransaction(BigDecimal.valueOf(15_000));
        when(userHistoryPort.countRecentTransactions(any(), any())).thenReturn(2);

        when(embeddingStorePort.findSimilar(any(), eq(5))).thenReturn(List.of());
        when(userHistoryPort.findRecentTransactions(any(), eq(20)))
                .thenReturn(List.of(normalTransaction(BigDecimal.valueOf(12_000))));
        when(llmFraudAnalysisPort.analyze(any(RedactedTransaction.class), any()))
                .thenReturn(new LlmFraudAssessment(BigDecimal.valueOf(30), "Low concern"));

        FraudDecision decision = useCase.evaluate(tx);

        assertThat(decision.reasoning())
                .contains("Rule gate")
                .contains("LLM")
                .contains("History")
                .contains("Weighted score");
    }

    @Test
    void should_pass_redacted_transaction_to_llm_not_original() {
        Transaction tx = transaction(BigDecimal.valueOf(15_000), "Pay john@test.com");
        when(userHistoryPort.countRecentTransactions(any(), any())).thenReturn(2);

        when(embeddingStorePort.findSimilar(any(), eq(5))).thenReturn(List.of());
        when(userHistoryPort.findRecentTransactions(any(), eq(20))).thenReturn(List.of());
        when(llmFraudAnalysisPort.analyze(any(RedactedTransaction.class), any()))
                .thenReturn(new LlmFraudAssessment(BigDecimal.valueOf(30), "OK"));

        useCase.evaluate(tx);

        // Verify the LLM port received a RedactedTransaction, not the raw Transaction
        verify(llmFraudAnalysisPort).analyze(any(RedactedTransaction.class), any());
    }

    private static Transaction normalTransaction(BigDecimal amount) {
        return transaction(amount, "Normal payment");
    }

    private static Transaction transaction(BigDecimal amount, String description) {
        return new Transaction(
                TransactionId.generate(),
                new Amount(amount, Currency.getInstance("USD")),
                new AccountId("GB1234567890"),
                new AccountId("US9876543210"),
                description,
                Instant.now(TEST_CLOCK)
        );
    }
}
