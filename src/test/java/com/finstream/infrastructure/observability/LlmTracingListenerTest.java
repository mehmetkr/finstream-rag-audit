package com.finstream.infrastructure.observability;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmTracingListenerTest {

    private InMemorySpanExporter spanExporter;
    private LlmTracingListener listener;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        var tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        var otel = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        listener = new LlmTracingListener(otel.getTracer("test"));
    }

    @Test
    void onRequestAndResponseCreatesSpanWithTokenUsage() {
        Map<Object, Object> attrs = new HashMap<>();
        var request = chatRequest("gpt-4o");

        listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OPEN_AI, attrs));

        var metadata = ChatResponseMetadata.builder()
                .modelName("gpt-4o")
                .tokenUsage(new TokenUsage(100, 50))
                .finishReason(FinishReason.STOP)
                .build();
        var response = ChatResponse.builder()
                .aiMessage(AiMessage.from("response"))
                .metadata(metadata)
                .build();

        listener.onResponse(new ChatModelResponseContext(response, request, ModelProvider.OPEN_AI, attrs));

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.getFirst();
        assertThat(span.getName()).isEqualTo("gen_ai.chat.completion");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.system")))
                .isEqualTo("openai");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")))
                .isEqualTo("gpt-4o");
        assertThat(span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")))
                .isEqualTo(100L);
        assertThat(span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens")))
                .isEqualTo(50L);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.finish_reason")))
                .isEqualTo("STOP");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }

    @Test
    void onResponseRecordsCost() {
        Map<Object, Object> attrs = new HashMap<>();
        var request = chatRequest("gpt-4o");

        listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OPEN_AI, attrs));

        var metadata = ChatResponseMetadata.builder()
                .modelName("gpt-4o")
                .tokenUsage(new TokenUsage(1000, 1000))
                .build();
        var response = ChatResponse.builder().aiMessage(AiMessage.from("response")).metadata(metadata).build();

        listener.onResponse(new ChatModelResponseContext(response, request, ModelProvider.OPEN_AI, attrs));

        SpanData span = spanExporter.getFinishedSpanItems().getFirst();
        assertThat(span.getAttributes().get(AttributeKey.doubleKey("gen_ai.usage.cost_usd")))
                .isGreaterThan(0.0);
    }

    @Test
    void onErrorSetsErrorStatus() {
        Map<Object, Object> attrs = new HashMap<>();
        var request = chatRequest("gpt-4o");

        listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OPEN_AI, attrs));
        listener.onError(new ChatModelErrorContext(
                new RuntimeException("API failure"), request, ModelProvider.OPEN_AI, attrs));

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        assertThat(spans.getFirst().getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void onResponseWithoutOnRequestIsNoOp() {
        var metadata = ChatResponseMetadata.builder().modelName("gpt-4o").build();
        var response = ChatResponse.builder().aiMessage(AiMessage.from("response")).metadata(metadata).build();
        var request = chatRequest("gpt-4o");

        // Should not throw — span is null, method returns early
        listener.onResponse(new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, new HashMap<>()));

        assertThat(spanExporter.getFinishedSpanItems()).isEmpty();
    }

    private static ChatRequest chatRequest(String modelName) {
        var params = DefaultChatRequestParameters.builder()
                .modelName(modelName)
                .build();
        return ChatRequest.builder()
                .messages(List.of(UserMessage.from("test")))
                .parameters(params)
                .build();
    }
}
