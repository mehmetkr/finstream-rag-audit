package com.finstream.infrastructure.observability;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TracedEmbeddingModelTest {

    private InMemorySpanExporter spanExporter;
    private TracedEmbeddingModel tracedModel;
    private EmbeddingModel delegate;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        var tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        var otel = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        delegate = mock(EmbeddingModel.class);
        tracedModel = new TracedEmbeddingModel(delegate,
                otel.getTracer("test"), "test-model", "test-system");
    }

    @AfterEach
    void cleanup() {
        AuditTraceContext.clear();
    }

    @Test
    void embedAllCreatesSpanWithAttributes() {
        var segments = List.of(TextSegment.from("hello"));
        var embedding = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});
        when(delegate.embedAll(segments))
                .thenReturn(Response.from(List.of(embedding), new TokenUsage(10, 0)));

        tracedModel.embedAll(segments);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.getFirst();
        assertThat(span.getName()).isEqualTo("rag.embedding");
        assertThat(span.getAttributes().get(AttributeKey.longKey("rag.embedding.segments_count")))
                .isEqualTo(1L);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.system")))
                .isEqualTo("test-system");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")))
                .isEqualTo("test-model");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }

    @Test
    void embedAllRecordsTokenUsage() {
        var segments = List.of(TextSegment.from("hello"));
        var embedding = Embedding.from(new float[]{0.1f});
        when(delegate.embedAll(segments))
                .thenReturn(Response.from(List.of(embedding), new TokenUsage(42, 0)));

        tracedModel.embedAll(segments);

        SpanData span = spanExporter.getFinishedSpanItems().getFirst();
        assertThat(span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")))
                .isEqualTo(42L);
    }

    @Test
    void embedAllRecordsExceptionOnFailure() {
        var segments = List.of(TextSegment.from("fail"));
        when(delegate.embedAll(segments)).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> tracedModel.embedAll(segments))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        assertThat(spans.getFirst().getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void embedAllAppliesBusinessContext() {
        AuditTraceContext.setCurrent(new AuditTraceContext("tx-123", "transaction", "embedding"));

        var segments = List.of(TextSegment.from("hello"));
        var embedding = Embedding.from(new float[]{0.1f});
        when(delegate.embedAll(segments))
                .thenReturn(Response.from(List.of(embedding)));

        tracedModel.embedAll(segments);

        SpanData span = spanExporter.getFinishedSpanItems().getFirst();
        assertThat(span.getAttributes().get(AttributeKey.stringKey("finstream.audit.id")))
                .isEqualTo("tx-123");
    }

    @Test
    void dimensionDelegatesToWrappedModel() {
        when(delegate.dimension()).thenReturn(384);
        assertThat(tracedModel.dimension()).isEqualTo(384);
    }
}
