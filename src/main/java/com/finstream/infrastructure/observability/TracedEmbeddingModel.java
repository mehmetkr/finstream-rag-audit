package com.finstream.infrastructure.observability;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.List;

/**
 * Decorator that wraps an EmbeddingModel with OpenTelemetry tracing.
 * Records embedding dimensions, segment count, and token usage.
 */
public class TracedEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final Tracer tracer;

    public TracedEmbeddingModel(EmbeddingModel delegate, OpenTelemetry openTelemetry) {
        this.delegate = delegate;
        this.tracer = openTelemetry.getTracer("finstream-rag-audit");
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        Span span = tracer.spanBuilder("rag.embedding")
                .setAttribute("rag.embedding.segments_count", textSegments.size())
                .setAttribute("gen_ai.system", "openai")
                .startSpan();

        // Apply business context if available
        AuditTraceContext ctx = AuditTraceContext.getCurrent();
        if (ctx != null) {
            ctx.applyToSpan(span);
        }

        try (Scope scope = span.makeCurrent()) {
            Response<List<Embedding>> response = delegate.embedAll(textSegments);

            if (response.content() != null && !response.content().isEmpty()) {
                span.setAttribute("rag.embedding.dimensions", response.content().get(0).dimension());
            }
            if (response.tokenUsage() != null) {
                span.setAttribute("gen_ai.usage.input_tokens", response.tokenUsage().inputTokenCount());

                // Cost tracking for embeddings
                double costUsd = ModelCostCalculator.calculateCost(
                        "text-embedding-3-small",  // or read from config
                        response.tokenUsage().inputTokenCount(),
                        0);
                span.setAttribute("gen_ai.usage.cost_usd", costUsd);
            }

            span.setStatus(StatusCode.OK);
            return response;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
