package com.finstream.infrastructure.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfiguration {

    @Bean
    Tracer otelTracer(OpenTelemetry openTelemetry,
                      @Value("${spring.application.name}") String appName) {
        return openTelemetry.getTracer(appName);
    }
}
