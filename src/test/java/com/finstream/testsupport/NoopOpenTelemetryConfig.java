package com.finstream.testsupport;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Provides a no-op OpenTelemetry instance for integration tests.
 * No-op tracers produce no-op spans that safely accept all attribute/status calls.
 * This avoids the timing issue where @MockitoBean creates an unstubbed mock
 * before @BeforeEach can configure it, causing NPEs in bean constructors.
 *
 * <p>The main {@code ObservabilityConfiguration} derives the OTel {@code Tracer}
 * bean from this no-op {@code OpenTelemetry}, so no separate test Tracer is needed.
 */
@TestConfiguration
public class NoopOpenTelemetryConfig {

    @Bean
    OpenTelemetry openTelemetry() {
        return OpenTelemetry.noop();
    }
}
