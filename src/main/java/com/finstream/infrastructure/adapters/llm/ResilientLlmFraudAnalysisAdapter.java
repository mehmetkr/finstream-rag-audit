package com.finstream.infrastructure.adapters.llm;

import com.finstream.domain.model.LlmFraudAssessment;
import com.finstream.domain.model.RedactedTransaction;
import com.finstream.domain.model.ScoredTransaction;
import com.finstream.domain.ports.outbound.LlmFraudAnalysisPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Decorator that wraps a {@link LlmFraudAnalysisPort} delegate with a
 * Resilience4j circuit breaker. When the circuit is open, calls fail fast
 * with {@link io.github.resilience4j.circuitbreaker.CallNotPermittedException},
 * which the upstream scatter-gather handles via graceful degradation.
 */
public class ResilientLlmFraudAnalysisAdapter implements LlmFraudAnalysisPort {

    private static final Logger log = LoggerFactory.getLogger(ResilientLlmFraudAnalysisAdapter.class);

    private final LlmFraudAnalysisPort delegate;
    private final CircuitBreaker circuitBreaker;

    public ResilientLlmFraudAnalysisAdapter(LlmFraudAnalysisPort delegate, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public LlmFraudAssessment analyze(RedactedTransaction transaction,
                                       List<ScoredTransaction> similarTransactions) {
        log.debug("LLM circuit breaker state: {}", circuitBreaker.getState());
        return circuitBreaker.executeSupplier(
                () -> delegate.analyze(transaction, similarTransactions));
    }
}
