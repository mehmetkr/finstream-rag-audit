package com.finstream.infrastructure.observability;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

/**
 * OpenTelemetry tracing listener for all LangChain4j ChatModel invocations.
 * Creates spans following the OpenTelemetry GenAI semantic conventions.
 *
 * <p>Not a Spring-managed bean. Register explicitly when wiring a real
 * {@code ChatLanguageModel} (e.g., via {@code .listeners(List.of(listener))}).
 *
 * <p>Span attributes follow: <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/">OTel GenAI Semantic Conventions</a>
 */
public class LlmTracingListener implements ChatModelListener {

    private static final String SPAN_KEY = "otel-span";
    private static final String SPAN_START_KEY = "otel-span-start-nanos";

    private final Tracer tracer;

    public LlmTracingListener(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        var params = requestContext.chatRequest().parameters();

        // Intentionally not calling span.makeCurrent(): the ChatModelListener lifecycle
        // spans onRequest → onResponse/onError which may execute on different threads.
        // The span is stored in the attributes map and retrieved by reference.
        Span span = tracer.spanBuilder("gen_ai.chat.completion")
                .setAttribute("gen_ai.system", "openai")
                .setAttribute("gen_ai.request.model", params.modelName() != null ? params.modelName() : "unknown")
                .startSpan();

        if (params.maxOutputTokens() != null) {
            span.setAttribute("gen_ai.request.max_tokens", params.maxOutputTokens());
        }

        if (params.temperature() != null) {
            span.setAttribute("gen_ai.request.temperature", params.temperature());
        }
        if (params.topP() != null) {
            span.setAttribute("gen_ai.request.top_p", params.topP());
        }

        // Store span in context for onResponse/onError
        requestContext.attributes().put(SPAN_KEY, span);
        requestContext.attributes().put(SPAN_START_KEY, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Span span = (Span) responseContext.attributes().get(SPAN_KEY);
        if (span == null) return;

        try {
            var metadata = responseContext.chatResponse().metadata();

            // Token usage
            TokenUsage usage = metadata.tokenUsage();
            if (usage != null) {
                span.setAttribute("gen_ai.usage.input_tokens", usage.inputTokenCount());
                span.setAttribute("gen_ai.usage.output_tokens", usage.outputTokenCount());
                span.setAttribute("gen_ai.usage.total_tokens", usage.totalTokenCount());

                // Cost tracking — only emit when pricing is known
                if (metadata.modelName() != null) {
                    double costUsd = ModelCostCalculator.calculateCost(
                            metadata.modelName(),
                            usage.inputTokenCount(),
                            usage.outputTokenCount());
                    if (costUsd > 0.0) {
                        span.setAttribute("gen_ai.usage.cost_usd", costUsd);
                    }
                }
            }

            // Response metadata
            span.setAttribute("gen_ai.response.model", metadata.modelName() != null ? metadata.modelName() : "unknown");
            if (metadata.finishReason() != null) {
                span.setAttribute("gen_ai.response.finish_reason", metadata.finishReason().name());
            }

            // Latency in milliseconds
            Long startNanos = (Long) responseContext.attributes().get(SPAN_START_KEY);
            if (startNanos != null) {
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                span.setAttribute("gen_ai.latency_ms", durationMs);
            }

            // Business context
            AuditTraceContext ctx = AuditTraceContext.getCurrent();
            if (ctx != null) {
                ctx.applyToSpan(span);
            }

            span.setStatus(StatusCode.OK);
        } finally {
            span.end();
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Span span = (Span) errorContext.attributes().get(SPAN_KEY);
        if (span == null) return;

        try {
            span.recordException(errorContext.error());
            span.setStatus(StatusCode.ERROR, errorContext.error().getMessage());
        } finally {
            span.end();
        }
    }
}
