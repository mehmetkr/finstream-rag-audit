package com.finstream.infrastructure.adapters.llm;

import com.finstream.domain.model.Amount;
import com.finstream.domain.model.LlmFraudAssessment;
import com.finstream.domain.model.RedactedAccountId;
import com.finstream.domain.model.RedactedTransaction;
import com.finstream.domain.model.ids.TransactionId;
import com.finstream.domain.ports.outbound.LlmFraudAnalysisPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientLlmFraudAnalysisAdapterTest {

    @Mock
    private LlmFraudAnalysisPort delegate;

    private CircuitBreaker circuitBreaker;
    private ResilientLlmFraudAnalysisAdapter adapter;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        circuitBreaker = registry.circuitBreaker("test-llm");
        adapter = new ResilientLlmFraudAnalysisAdapter(delegate, circuitBreaker);
    }

    @Test
    void should_delegate_to_underlying_adapter_on_success() {
        LlmFraudAssessment expected = new LlmFraudAssessment(BigDecimal.valueOf(42), "Analysis result");
        when(delegate.analyze(any(), any())).thenReturn(expected);

        LlmFraudAssessment result = adapter.analyze(redactedTransaction(), List.of());

        assertThat(result).isEqualTo(expected);
        verify(delegate).analyze(any(), any());
    }

    @Test
    void should_propagate_exception_when_delegate_fails() {
        when(delegate.analyze(any(), any())).thenThrow(new RuntimeException("LLM timeout"));

        assertThatThrownBy(() -> adapter.analyze(redactedTransaction(), List.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LLM timeout");
    }

    @Test
    void should_open_circuit_after_failure_threshold_and_reject_calls() {
        when(delegate.analyze(any(), any())).thenThrow(new RuntimeException("LLM down"));

        // Fill the sliding window with failures to trip the breaker
        for (int i = 0; i < 4; i++) {
            try {
                adapter.analyze(redactedTransaction(), List.of());
            } catch (RuntimeException ignored) {
                // expected
            }
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Next call should be rejected without reaching the delegate
        assertThatThrownBy(() -> adapter.analyze(redactedTransaction(), List.of()))
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void should_stay_closed_when_failure_rate_is_below_threshold() {
        LlmFraudAssessment success = new LlmFraudAssessment(BigDecimal.valueOf(30), "OK");

        // 3 successes, 1 failure = 25% failure rate (below 50% threshold)
        when(delegate.analyze(any(), any()))
                .thenReturn(success)
                .thenReturn(success)
                .thenReturn(success)
                .thenThrow(new RuntimeException("transient"));

        for (int i = 0; i < 3; i++) {
            adapter.analyze(redactedTransaction(), List.of());
        }
        try {
            adapter.analyze(redactedTransaction(), List.of());
        } catch (RuntimeException ignored) {
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void should_transition_to_half_open_after_wait_duration() {
        // Use a tiny wait duration for testing with automatic transition enabled
        CircuitBreakerConfig fastConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(50))
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        CircuitBreaker fastBreaker = CircuitBreakerRegistry.of(fastConfig)
                .circuitBreaker("fast-test");
        ResilientLlmFraudAnalysisAdapter fastAdapter =
                new ResilientLlmFraudAnalysisAdapter(delegate, fastBreaker);

        when(delegate.analyze(any(), any())).thenThrow(new RuntimeException("down"));

        // Trip the breaker
        for (int i = 0; i < 2; i++) {
            try {
                fastAdapter.analyze(redactedTransaction(), List.of());
            } catch (RuntimeException ignored) {
            }
        }
        assertThat(fastBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Wait for automatic transition
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(fastBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    private static RedactedTransaction redactedTransaction() {
        return new RedactedTransaction(
                TransactionId.generate(),
                new Amount(BigDecimal.valueOf(5000), Currency.getInstance("USD")),
                new RedactedAccountId("a1b2c3d4"),
                new RedactedAccountId("e5f6a7b8"),
                "Redacted payment description",
                Instant.now()
        );
    }
}
