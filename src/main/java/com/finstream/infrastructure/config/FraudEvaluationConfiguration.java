package com.finstream.infrastructure.config;

import com.finstream.application.FraudEvaluationUseCaseImpl;
import com.finstream.domain.model.FraudEvaluationConfig;
import com.finstream.domain.ports.inbound.FraudEvaluationUseCase;
import com.finstream.domain.ports.outbound.EmbeddingStorePort;
import com.finstream.domain.ports.outbound.LlmFraudAnalysisPort;
import com.finstream.domain.ports.outbound.UserHistoryPort;
import com.finstream.domain.service.RuleGateService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FraudEvaluationProperties.class)
public class FraudEvaluationConfiguration {

    @Bean
    RuleGateService ruleGateService(FraudEvaluationProperties properties) {
        return new RuleGateService(properties.amountThreshold(), properties.velocityLimit());
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
                                                   FraudEvaluationConfig config) {
        return new FraudEvaluationUseCaseImpl(
                ruleGateService, userHistoryPort, embeddingStorePort,
                llmFraudAnalysisPort, config);
    }
}
