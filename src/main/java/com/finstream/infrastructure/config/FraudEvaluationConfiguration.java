package com.finstream.infrastructure.config;

import com.finstream.application.FraudEvaluationUseCaseImpl;
import com.finstream.domain.model.FraudEvaluationConfig;
import com.finstream.domain.ports.inbound.FraudEvaluationUseCase;
import com.finstream.domain.ports.outbound.EmbeddingStorePort;
import com.finstream.domain.ports.outbound.LlmFraudAnalysisPort;
import com.finstream.domain.ports.outbound.UserHistoryPort;
import com.finstream.domain.service.PiiRedactor;
import com.finstream.domain.service.RuleGateService;
import com.finstream.infrastructure.adapters.llm.ResilientLlmFraudAnalysisAdapter;
import com.finstream.infrastructure.adapters.llm.StubLlmFraudAnalysisAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(FraudEvaluationProperties.class)
public class FraudEvaluationConfiguration {

    @Bean
    RuleGateService ruleGateService(FraudEvaluationProperties properties) {
        return new RuleGateService(properties.amountThreshold(), properties.velocityLimit());
    }

    @Bean
    PiiRedactor piiRedactor() {
        return new PiiRedactor();
    }

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .slidingWindowSize(10)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    LlmFraudAnalysisPort llmFraudAnalysisPort(CircuitBreakerRegistry registry) {
        CircuitBreaker cb = registry.circuitBreaker("llm-analysis");
        return new ResilientLlmFraudAnalysisAdapter(new StubLlmFraudAnalysisAdapter(), cb);
    }

    @Bean
    FraudEvaluationConfig fraudEvaluationConfig(FraudEvaluationProperties p) {
        return new FraudEvaluationConfig(
                p.velocityWindow(),
                p.ragMaxResults(),
                p.historyLimit(),
                p.weights().ruleGate(),
                p.weights().rag(),
                p.weights().llm(),
                p.thresholds().approveMax(),
                p.thresholds().flagMax()
        );
    }

    @Bean
    FraudEvaluationUseCase fraudEvaluationUseCase(RuleGateService ruleGateService,
                                                   UserHistoryPort userHistoryPort,
                                                   EmbeddingStorePort embeddingStorePort,
                                                   LlmFraudAnalysisPort llmFraudAnalysisPort,
                                                   PiiRedactor piiRedactor,
                                                   FraudEvaluationConfig config) {
        return new FraudEvaluationUseCaseImpl(
                ruleGateService, userHistoryPort, embeddingStorePort,
                llmFraudAnalysisPort, piiRedactor, config);
    }
}
