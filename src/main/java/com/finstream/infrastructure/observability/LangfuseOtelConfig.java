package com.finstream.infrastructure.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "langfuse.enabled", havingValue = "true", matchIfMissing = false)
public class LangfuseOtelConfig {

    @Bean
    public OpenTelemetry openTelemetry(
            @Value("${langfuse.endpoint}") String endpoint,
            @Value("${LANGFUSE_AUTH_BASE64}") String authBase64,
            @Value("${otel.service.name:finstream-rag-audit}") String serviceName) {

        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint + "/v1/traces")
                .addHeader("Authorization", "Basic " + authBase64)
                .build();

        Resource resource = Resource.getDefault().toBuilder()
                .put("service.name", serviceName)
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .setResource(resource)
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("finstream-rag-audit", "1.0.0");
    }
}
