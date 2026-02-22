package com.finstream.testsupport;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Provides a no-op OpenTelemetry instance for integration tests.
 * No-op tracers produce no-op spans that safely accept all attribute/status calls.
 * This avoids the timing issue where @MockitoBean creates an unstubbed mock
 * before @BeforeEach can configure it, causing NPEs in bean constructors.
 */
@TestConfiguration
public class NoopOpenTelemetryConfig {

    @Bean
    OpenTelemetry openTelemetry() {
        return OpenTelemetry.noop();
    }
}
