# PLAN: Langfuse Observability Integration for FinStream-RAG-Audit

## Context

This plan adds production-grade LLM observability to the FinStream-RAG-Audit project using OpenTelemetry exported to Langfuse. The project is a Spring Boot 3.x application using LangChain4j for RAG with pgvector and OpenAI models. It follows hexagonal architecture.

**Integration path:** LangChain4j → OpenTelemetry → OTLP HTTP exporter → Langfuse

Langfuse acts as an OpenTelemetry backend. There is no Langfuse-specific Java SDK required. We use the standard OpenTelemetry Java SDK with the OTLP HTTP exporter pointed at Langfuse's `/api/public/otel` endpoint. Langfuse does **NOT** support gRPC — use HTTP/protobuf only.

---

## Architecture Decisions

- **Hexagonal architecture compliance:** All observability code lives in the infrastructure/adapter layer. Core domain logic must have zero OTel imports. Tracing is an outbound adapter concern.
- **LangChain4j ChatModelListener:** Used for LLM call instrumentation. This is the official LangChain4j observability hook — it provides `onRequest`, `onResponse`, and `onError` callbacks with full access to token usage, model metadata, and request/response content.
- **Manual spans for RAG stages:** Embedding operations are wrapped with a manual OTel span via a decorator. Retrieval operations in `PgVectorEmbeddingAdapter` are instrumented directly with a manual span because the project uses `EmbeddingStore` directly rather than `ContentRetriever`.
- **LLM Instrumentation:** Since the project currently uses `StubLlmFraudAnalysisAdapter`, the `LlmTracingListener` will be implemented and made available for future use when a real `ChatLanguageModel` is enabled. A comment will be added to the Stub adapter.
- **OTLP HTTP exporter:** Configured as a Spring `@Configuration` bean. Exports to Langfuse cloud (or self-hosted) via Basic Auth.
- **Cost tracking:** Calculated per-request using a model pricing map and recorded as span attributes. Langfuse aggregates these automatically.

---

## Prerequisites

Before starting implementation:

1. The implementer must have a Langfuse account. Sign up at https://cloud.langfuse.com (free Hobby tier — unlimited traces).
2. Create a project called `finstream-rag-audit` in the Langfuse dashboard.
3. Copy the **Public Key** (`pk-lf-...`) and **Secret Key** (`sk-lf-...`) from Project Settings → API Keys.
4. Generate the Base64 auth string:
   ```bash
   echo -n "pk-lf-YOUR_PUBLIC_KEY:sk-lf-YOUR_SECRET_KEY" | base64
   ```
5. The project must already have LangChain4j dependencies and a working RAG pipeline with pgvector.

---

## Sub-task 1: Environment Configuration

**Goal:** Add Langfuse connection settings to the project without touching any Java code.

**Files to create/modify:**

### 1a. Add to `.env` (or `.env.local` — must be in `.gitignore`):
```properties
LANGFUSE_PUBLIC_KEY=pk-lf-REPLACE_ME
LANGFUSE_SECRET_KEY=sk-lf-REPLACE_ME
LANGFUSE_AUTH_BASE64=REPLACE_WITH_BASE64_STRING
```

### 1b. Add to `application.yml` (or `application.properties`):
```yaml
langfuse:
  enabled: true
  endpoint: https://cloud.langfuse.com/api/public/otel
  # For EU region use: https://cloud.langfuse.com/api/public/otel
  # For US region use: https://us.cloud.langfuse.com/api/public/otel
  # For self-hosted use: http://localhost:3000/api/public/otel

otel:
  service:
    name: finstream-rag-audit
  exporter:
    otlp:
      endpoint: ${langfuse.endpoint}
      headers: "Authorization=Basic ${LANGFUSE_AUTH_BASE64}"
```

### 1c. Add to `.env.example` (safe to commit — no real keys):
```properties
LANGFUSE_PUBLIC_KEY=pk-lf-your-public-key
LANGFUSE_SECRET_KEY=sk-lf-your-secret-key
LANGFUSE_AUTH_BASE64=base64-of-public:secret
```

**Validation:** No runtime validation yet — this is config only.

**Commit message:** `chore: add Langfuse environment configuration`

---

## Sub-task 2: Maven Dependencies

**Goal:** Add OpenTelemetry SDK, OTLP HTTP exporter, and instrumentation dependencies.

**File to modify:** `pom.xml`

### 2a. Add to `<dependencyManagement>`:
```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-bom</artifactId>
    <version>1.46.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-instrumentation-bom</artifactId>
    <version>2.17.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

### 2b. Add to `<dependencies>`:
```xml
<!-- OpenTelemetry SDK core -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
</dependency>

<!-- OTLP HTTP exporter (NOT gRPC — Langfuse requires HTTP/protobuf) -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>

<!-- OpenTelemetry API for manual span creation -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
</dependency>

<!-- OpenTelemetry Spring Boot starter (auto-configures TracerProvider) -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
</dependency>
```

**Important version notes:**
- OpenTelemetry BOM version `1.46.0` and instrumentation BOM `2.17.0` are current as of early 2026. Check Maven Central for latest.
- The OTel BOM manages all `io.opentelemetry` version alignment automatically.
- LangChain4j BOM should already be present in the project. Verify it is version `0.36.x` or later (required for `ChatModelListener` support).

**Validation:** Run `mvn dependency:tree | grep opentelemetry` — should show sdk, api, exporter-otlp artifacts.

**Commit message:** `build: add OpenTelemetry SDK and OTLP exporter dependencies`

---

## Sub-task 3: OTel TracerProvider Configuration (Auto-configured)

**Goal:** Use Spring Boot's native `management.otlp.tracing` properties to configure the exporter, deleting the manual config bean plan.

**File to modify:** `src/main/resources/application.yml`

```yaml
management:
  otlp:
    tracing:
      endpoint: ${langfuse.endpoint}/v1/traces
      headers:
        Authorization: "Basic ${LANGFUSE_AUTH_BASE64}"
      transport: http # Force HTTP for Langfuse
```

**File to delete (if created):** `src/main/java/com/finstream/infrastructure/observability/LangfuseOtelConfig.java`

**Dependency Note:** This relies on `io.opentelemetry:opentelemetry-exporter-otlp` being on the classpath (already done).

**Tracer Injection:** Since we don't manually create the `Tracer` bean, listeners will inject `OpenTelemetry` (auto-configured by Spring) and obtain a tracer via `openTelemetry.getTracer("finstream-rag-audit")`.

---

## Sub-task 4: LLM Call Instrumentation via ChatModelListener

**Goal:** Create a `ChatModelListener` implementation that wraps every LangChain4j LLM call in an OTel span with GenAI semantic attributes, token usage, and latency.

**File to create:** `src/main/java/com/finstream/infrastructure/observability/LlmTracingListener.java`

```java
package com.finstream.infrastructure.observability;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Component;

/**
 * OpenTelemetry tracing listener for all LangChain4j ChatModel invocations.
 * Creates spans following the OpenTelemetry GenAI semantic conventions.
 *
 * Span attributes follow: https://opentelemetry.io/docs/specs/semconv/gen-ai/
 */
@Component
public class LlmTracingListener implements ChatModelListener {

    private static final String SPAN_KEY = "otel-span";
    private static final String SPAN_START_KEY = "otel-span-start-nanos";

    private final Tracer tracer;

    public LlmTracingListener(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("finstream-rag-audit");
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        var params = requestContext.chatRequest().parameters();

        Span span = tracer.spanBuilder("gen_ai.chat.completion")
                .setAttribute("gen_ai.system", "openai")
                .setAttribute("gen_ai.request.model", params.modelName() != null ? params.modelName() : "unknown")
                .setAttribute("gen_ai.request.max_tokens", params.maxOutputTokens() != null ? params.maxOutputTokens() : 0)
                .startSpan();

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

                // Cost tracking (see Sub-task 6 — ModelCostCalculator)
                if (metadata.modelName() != null) {
                    double costUsd = ModelCostCalculator.calculateCost(
                            metadata.modelName(),
                            usage.inputTokenCount(),
                            usage.outputTokenCount());
                    span.setAttribute("gen_ai.usage.cost_usd", costUsd);
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

            // Business context (see Sub-task 7 — AuditTraceContext)
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
```

**File to modify:** Wherever the `ChatModel` (or `ChatLanguageModel`) bean is created. Register the listener:

```java
// Find the existing ChatModel/ChatLanguageModel bean definition and add the listener.
// Example:

@Bean
public ChatLanguageModel chatLanguageModel(
        @Value("${openai.api-key}") String apiKey,
        @Value("${openai.model-name:gpt-4o}") String modelName,
        LlmTracingListener tracingListener) {  // <-- inject the listener

    return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .listeners(List.of(tracingListener))  // <-- register here
            .build();
}
```

**Important:** If the project uses `@AiService` annotations with auto-configured models via `application.yml`, the listener must be registered differently. In that case, create a `BeanPostProcessor` that wraps the auto-configured model:

```java
@Component
public class ChatModelListenerRegistrar implements BeanPostProcessor {

    private final LlmTracingListener tracingListener;

    public ChatModelListenerRegistrar(LlmTracingListener tracingListener) {
        this.tracingListener = tracingListener;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof OpenAiChatModel model) {
            // OpenAiChatModel supports adding listeners after construction
            // Check the actual API — if addListener() exists, use it.
            // Otherwise, rebuild the model with the listener included.
        }
        return bean;
    }
}
```

**Validation:**
1. Trigger a RAG query that invokes the LLM.
2. Check Langfuse → Traces. You should see a trace with a `gen_ai.chat.completion` span.
3. Click the span. Verify these attributes are present:
   - `gen_ai.system` = "openai"
   - `gen_ai.request.model` = the model name
   - `gen_ai.usage.input_tokens` = a positive integer
   - `gen_ai.usage.output_tokens` = a positive integer
   - `gen_ai.response.model` = the response model name
   - `gen_ai.latency_ms` = duration in milliseconds

**Commit message:** `feat: instrument LangChain4j ChatModel with OpenTelemetry tracing`

---

## Sub-task 5: RAG Pipeline Instrumentation (Embedding + Retrieval Spans)

**Goal:** Add OTel spans for the embedding generation and vector retrieval stages of the RAG pipeline, so a single query produces a parent trace with child spans: `rag.query` → `rag.embedding` → `rag.retrieval` → `gen_ai.chat.completion`.

### 5a. Traced Retrieval (Manual Span in Adapter)

**Goal:** Instrument the `findSimilar` method in `PgVectorEmbeddingAdapter` to record retrieval metrics.

**File to modify:** `src/main/java/com/finstream/infrastructure/adapters/embedding/PgVectorEmbeddingAdapter.java`

Inject `Tracer` and wrap the search logic:

```java
// ...
private final Tracer tracer;

public PgVectorEmbeddingAdapter(..., Tracer tracer) {
    // ...
    this.tracer = tracer;
}

@Override
public List<ScoredTransaction> findSimilar(Transaction transaction, int maxResults) {
    Span span = tracer.spanBuilder("rag.retrieval")
            .setAttribute("rag.store.type", "pgvector")
            .startSpan();
    
    // Apply business context if available
    AuditTraceContext ctx = AuditTraceContext.getCurrent();
    if (ctx != null) {
        ctx.applyToSpan(span);
    }

    try (Scope scope = span.makeCurrent()) {
        // ... existing search logic ...
        List<ScoredTransaction> results = ...;
        
        span.setAttribute("rag.results.count", results.size());
        span.setStatus(StatusCode.OK);
        return results;
    } catch (Exception e) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR, e.getMessage());
        throw e;
    } finally {
        span.end();
    }
}
```

### 5b. Traced Embedding Model (Decorator)

**File to create:** `src/main/java/com/finstream/infrastructure/observability/TracedEmbeddingModel.java`

```java
package com.finstream.infrastructure.observability;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
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

    public TracedEmbeddingModel(EmbeddingModel delegate, Tracer tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
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
```

### 5c. Wire decorated beans into Spring context

**File to modify:** The Spring configuration where `ContentRetriever` and `EmbeddingModel` beans are defined.

```java
// Wrap the embedding model
@Bean
public EmbeddingModel embeddingModel(
        // ...
        Tracer tracer) {
    // ...
    return new TracedEmbeddingModel(base, tracer);
}

// Note: ContentRetriever wrapping is skipped as PgVectorEmbeddingAdapter uses EmbeddingStore directly.
// Retrieval tracing is handled inside PgVectorEmbeddingAdapter.
```

### 5d. Parent span for the full RAG query

**File to create:** `src/main/java/com/finstream/infrastructure/observability/TracedRagOperation.java`

```java
package com.finstream.infrastructure.observability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for methods that should be traced as RAG query operations.
 * Apply to the service method that orchestrates the full RAG pipeline.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TracedRagOperation {
}
```

**File to create:** `src/main/java/com/finstream/infrastructure/observability/TracedRagQueryAspect.java`

```java
package com.finstream.infrastructure.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that creates a parent "rag.query" span around RAG service methods.
 * The child spans (embedding, retrieval, LLM) will nest under this automatically
 * via OTel context propagation.
 *
 * Requires spring-boot-starter-aop dependency.
 */
@Aspect
@Component
public class TracedRagQueryAspect {

    private final Tracer tracer;

    public TracedRagQueryAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    @Around("@annotation(com.finstream.infrastructure.observability.TracedRagOperation)")
    public Object traceRagQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        Span span = tracer.spanBuilder("rag.query")
                .setAttribute("rag.pipeline", "finstream-audit")
                .startSpan();

        // Apply business context if available
        AuditTraceContext ctx = AuditTraceContext.getCurrent();
        if (ctx != null) {
            ctx.applyToSpan(span);
        }

        try (Scope scope = span.makeCurrent()) {
            Object result = joinPoint.proceed();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getMessage());
            throw t;
        } finally {
            span.end();
        }
    }
}
```

Then annotate the RAG entry point method in the service layer:
```java
@TracedRagOperation  // <-- add this annotation
public String processAuditQuery(String question) {
    // existing RAG pipeline code — no changes needed here
}
```

**Note:** If `spring-boot-starter-aop` is not already in the project, add it:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

**Alternatively**, if you prefer not to use AOP, create the parent span manually in the service method instead of using the annotation + aspect approach.

**Validation:**
1. Trigger a RAG query.
2. Check Langfuse → Traces. You should see a single trace with nested spans:
   ```
   rag.query (parent)
   ├── rag.embedding (child)
   ├── rag.retrieval (child)
   └── gen_ai.chat.completion (child)
   ```
3. Each span should have its own latency, attributes, and status.
4. The `rag.retrieval` span should show `rag.results.count`.
5. The `rag.embedding` span should show `rag.embedding.dimensions`.

**Commit message:** `feat: add distributed tracing for RAG embedding and retrieval operations`

---

## Sub-task 6: Token Cost Tracking

**Goal:** Calculate and record the USD cost of each LLM and embedding call as a span attribute, enabling cost-per-query and cost-per-audit dashboards in Langfuse.

**File to create:** `src/main/java/com/finstream/infrastructure/observability/ModelCostCalculator.java`

```java
package com.finstream.infrastructure.observability;

import java.util.Map;

/**
 * Calculates the USD cost of LLM and embedding API calls based on
 * per-token pricing for each model. Prices are per 1,000 tokens.
 *
 * Update prices when model pricing changes. Source: https://openai.com/pricing
 */
public class ModelCostCalculator {

    public record ModelPricing(double inputPer1kTokens, double outputPer1kTokens) {}

    // Prices as of early 2026 — update as needed
    private static final Map<String, ModelPricing> PRICING = Map.of(
            "gpt-4o", new ModelPricing(0.0025, 0.0100),
            "gpt-4o-mini", new ModelPricing(0.000150, 0.000600),
            "gpt-4-turbo", new ModelPricing(0.0100, 0.0300),
            "gpt-4", new ModelPricing(0.0300, 0.0600),
            "gpt-3.5-turbo", new ModelPricing(0.0005, 0.0015),
            "text-embedding-3-small", new ModelPricing(0.00002, 0.0),
            "text-embedding-3-large", new ModelPricing(0.00013, 0.0)
    );

    /**
     * Calculates total cost in USD for a single API call.
     *
     * @param modelName    the model identifier (e.g., "gpt-4o")
     * @param inputTokens  number of input/prompt tokens
     * @param outputTokens number of output/completion tokens (0 for embeddings)
     * @return cost in USD, or 0.0 if model pricing is unknown
     */
    public static double calculateCost(String modelName, int inputTokens, int outputTokens) {
        ModelPricing pricing = PRICING.get(modelName);
        if (pricing == null) {
            // Try matching with prefix (handles versioned model names like "gpt-4o-2024-08-06")
            pricing = PRICING.entrySet().stream()
                    .filter(e -> modelName != null && modelName.startsWith(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        if (pricing == null) return 0.0;

        return (inputTokens / 1000.0) * pricing.inputPer1kTokens()
                + (outputTokens / 1000.0) * pricing.outputPer1kTokens();
    }
}
```

**Note:** The cost calculation calls are already included inline in `LlmTracingListener.java` (Sub-task 4) and `TracedEmbeddingModel.java` (Sub-task 5b) in the code above. This sub-task creates the shared `ModelCostCalculator` class that those classes reference. Implement this class **before** or **alongside** Sub-tasks 4 and 5 to avoid compilation errors.

**Validation:**
1. Trigger a RAG query.
2. Check Langfuse → click on the `gen_ai.chat.completion` span.
3. Verify `gen_ai.usage.cost_usd` is present and has a reasonable value (e.g., $0.001–$0.05 per query).
4. Check the embedding span for the same attribute.
5. In Langfuse dashboard, navigate to Analytics → Cost. You should see cost aggregation across traces.

**Commit message:** `feat: add token-level cost attribution to LLM traces`

---

## Sub-task 7: Business Context Span Attributes

**Goal:** Enrich traces with FinStream-specific business context so traces can be filtered by audit ID, document type, and pipeline stage in the Langfuse dashboard.

**File to create:** `src/main/java/com/finstream/infrastructure/observability/AuditTraceContext.java`

```java
package com.finstream.infrastructure.observability;

import io.opentelemetry.api.trace.Span;

/**
 * Carries business context through the observability pipeline.
 * Stored in a ThreadLocal so all spans within a request can access it.
 *
 * IMPORTANT: Always call clear() in a finally block to prevent ThreadLocal leaks.
 */
public class AuditTraceContext {

    private static final ThreadLocal<AuditTraceContext> CURRENT = new ThreadLocal<>();

    private final String auditId;
    private final String documentType;
    private final String pipelineStage;

    public AuditTraceContext(String auditId, String documentType, String pipelineStage) {
        this.auditId = auditId;
        this.documentType = documentType;
        this.pipelineStage = pipelineStage;
    }

    public static void setCurrent(AuditTraceContext context) {
        CURRENT.set(context);
    }

    public static AuditTraceContext getCurrent() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public String auditId() { return auditId; }
    public String documentType() { return documentType; }
    public String pipelineStage() { return pipelineStage; }

    /**
     * Apply business context attributes to the given span.
     */
    public void applyToSpan(Span span) {
        if (auditId != null) span.setAttribute("finstream.audit.id", auditId);
        if (documentType != null) span.setAttribute("finstream.document.type", documentType);
        if (pipelineStage != null) span.setAttribute("finstream.pipeline.stage", pipelineStage);
    }
}
```

**Note:** The `AuditTraceContext.applyToSpan()` calls are already included in `LlmTracingListener`, `TracedContentRetriever`, `TracedEmbeddingModel`, and `TracedRagQueryAspect` in the code above. This sub-task creates the shared class. Implement **before** or **alongside** Sub-tasks 4 and 5 to avoid compilation errors.

**File to modify:** The service/controller that initiates audit queries — set the context before processing:

```java
// At the start of an audit query processing method:
AuditTraceContext.setCurrent(new AuditTraceContext(
        audit.getId(),             // e.g., "AUDIT-2026-001"
        document.getType(),        // e.g., "financial_statement", "balance_sheet"
        "rag_query"                // pipeline stage identifier
));

try {
    // ... process the RAG query (all spans will inherit business context) ...
} finally {
    AuditTraceContext.clear();  // ALWAYS clean up ThreadLocal
}
```

**Validation:**
1. Trigger a RAG query with a known audit ID.
2. In Langfuse, search traces by attribute: `finstream.audit.id = <your-audit-id>`.
3. Verify all spans within the trace carry the business context attributes.
4. Filter by `finstream.document.type` to confirm document-level filtering works.

**Commit message:** `feat: enrich traces with business context attributes for audit traceability`

---

## Sub-task 8: README Update

**Goal:** Add an Observability section to the project README with architecture description and setup instructions.

**File to modify:** `README.md`

Add the following section after the existing architecture documentation:

````markdown
## Observability

### Architecture

FinStream-RAG-Audit uses OpenTelemetry for distributed tracing, exported to
[Langfuse](https://langfuse.com) for LLM-specific observability. Every RAG
pipeline execution produces a trace with nested spans:

```
rag.query (parent span)
├── rag.embedding       → vector generation via OpenAI text-embedding-3-small
├── rag.retrieval       → pgvector similarity search with result count
└── gen_ai.chat.completion → LLM generation with token usage and cost
```

Each span records:
- **Token usage**: input tokens, output tokens, total tokens
- **Cost attribution**: USD cost per call based on model pricing
- **Business context**: audit ID, document type, pipeline stage
- **Error tracking**: exceptions, error messages, stack traces

### Setup

1. Sign up at [Langfuse Cloud](https://cloud.langfuse.com) (free tier available)
2. Create a project and copy your API keys
3. Configure environment variables:
   ```bash
   export LANGFUSE_PUBLIC_KEY=pk-lf-...
   export LANGFUSE_SECRET_KEY=sk-lf-...
   export LANGFUSE_AUTH_BASE64=$(echo -n "$LANGFUSE_PUBLIC_KEY:$LANGFUSE_SECRET_KEY" | base64)
   ```
4. Set `langfuse.enabled=true` in your application profile
5. Run the application and trigger a query — traces appear in the Langfuse dashboard

### Dashboard

![Langfuse Trace View](docs/images/langfuse-trace-example.png)

### Cost Tracking

Token costs are calculated per-request and recorded as span attributes. The
Langfuse Analytics dashboard aggregates costs by time period, model, and
custom dimensions (audit ID, document type).
````

**Additional action:** Take a screenshot of a Langfuse trace showing the nested span hierarchy and save it as `docs/images/langfuse-trace-example.png`. Reference it in the README.

**Commit message:** `docs: add observability architecture and Langfuse setup instructions`

---

## File Summary

### New files to create:

| File | Sub-task | Purpose |
|------|----------|---------|
| `infrastructure/observability/LangfuseOtelConfig.java` | 3 | OTel TracerProvider + OTLP exporter config |
| `infrastructure/observability/LlmTracingListener.java` | 4 | ChatModelListener for LLM span creation |
| `infrastructure/observability/TracedContentRetriever.java` | 5a | Decorator for retrieval span creation |
| `infrastructure/observability/TracedEmbeddingModel.java` | 5b | Decorator for embedding span creation |
| `infrastructure/observability/TracedRagOperation.java` | 5d | Marker annotation for RAG entry points |
| `infrastructure/observability/TracedRagQueryAspect.java` | 5d | AOP aspect for parent rag.query span |
| `infrastructure/observability/ModelCostCalculator.java` | 6 | Token cost calculation by model |
| `infrastructure/observability/AuditTraceContext.java` | 7 | ThreadLocal business context carrier |

### Files to modify:

| File | Sub-task | Change |
|------|----------|--------|
| `pom.xml` | 2 | Add OTel BOM + dependencies |
| `application.yml` | 1 | Add langfuse config properties |
| `.env.example` | 1 | Add example env vars |
| ChatModel bean definition | 4 | Register `LlmTracingListener` |
| ContentRetriever bean definition | 5c | Wrap with `TracedContentRetriever` |
| EmbeddingModel bean definition | 5c | Wrap with `TracedEmbeddingModel` |
| RAG service entry point method | 5d | Add `@TracedRagOperation` annotation |
| Audit query controller/service | 7 | Set/clear `AuditTraceContext` |
| `README.md` | 8 | Add Observability section |

---

## Execution Order

**Recommended build order (respects compilation dependencies):**

1. **Sub-task 1** — Environment config (no Java changes)
2. **Sub-task 2** — Maven dependencies (no Java changes)
3. **Sub-task 6** — `ModelCostCalculator.java` (standalone, no dependencies on other new files)
4. **Sub-task 7** — `AuditTraceContext.java` (standalone, no dependencies on other new files)
5. **Sub-task 3** — `LangfuseOtelConfig.java` (depends on OTel deps from sub-task 2)
6. **Sub-task 4** — `LlmTracingListener.java` (depends on Tracer bean from sub-task 3, references ModelCostCalculator and AuditTraceContext)
7. **Sub-task 5** — RAG tracing decorators + aspect (depends on Tracer bean, references ModelCostCalculator and AuditTraceContext)
8. **Sub-task 8** — README (depends on everything above being working and verified)

**Each sub-task should produce a separate git commit.**

**Important: Sub-tasks 6 and 7 should be implemented before sub-tasks 4 and 5** because the listener and decorator code references `ModelCostCalculator` and `AuditTraceContext`. If implementing strictly in sub-task order (1→2→3→4→5→6→7→8), initially stub out the cost and context calls in sub-tasks 4/5 and add them in sub-tasks 6/7.

---

## Testing Strategy

### Unit tests (create alongside each sub-task):

**`ModelCostCalculatorTest.java`:**
- Test cost calculation for known models (gpt-4o, gpt-3.5-turbo, text-embedding-3-small)
- Test with unknown model name → returns 0.0
- Test with versioned model name (e.g., "gpt-4o-2024-08-06") → matches prefix
- Test with null model name → returns 0.0
- Test with 0 tokens → returns 0.0
- Pure function — no mocking needed.

**`AuditTraceContextTest.java`:**
- Test ThreadLocal set/get/clear lifecycle
- Test clear() actually removes context (getCurrent returns null after clear)
- Test applyToSpan with mock Span — verify setAttribute called with correct keys
- Test with null fields — verify no NPE, attributes not set

**`LlmTracingListenerTest.java`:**
- Test onRequest creates span with expected attributes
- Test onResponse records token usage and ends span
- Test onError records exception and sets ERROR status
- Test onResponse with null span in attributes (defensive)
- Mock: `Tracer`, `Span`, `ChatModelRequestContext`, `ChatModelResponseContext`

**`TracedContentRetrieverTest.java`:**
- Test successful retrieval creates span with result count
- Test exception propagates and records on span
- Test delegate is called exactly once
- Mock: `ContentRetriever` (delegate), `Tracer`, `Span`

**`TracedEmbeddingModelTest.java`:**
- Test successful embedding creates span with dimensions
- Test exception propagates and records on span
- Mock: `EmbeddingModel` (delegate), `Tracer`, `Span`

### Integration tests:

Create a test profile with `langfuse.enabled=false` to disable tracing during standard test runs.

For observability-specific integration tests, use an `InMemorySpanExporter`:

```java
@TestConfiguration
public class TestOtelConfig {

    @Bean
    public InMemorySpanExporter spanExporter() {
        return InMemorySpanExporter.create();
    }

    @Bean
    public SdkTracerProvider testTracerProvider(InMemorySpanExporter exporter) {
        return SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
    }

    @Bean
    public Tracer testTracer(SdkTracerProvider provider) {
        return provider.get("test");
    }
}
```

Then assert on exported spans:
```java
List<SpanData> spans = spanExporter.getFinishedSpanItems();
assertThat(spans).hasSize(4);  // rag.query, rag.embedding, rag.retrieval, gen_ai.chat.completion
assertThat(spans).extracting(SpanData::getName)
        .containsExactlyInAnyOrder("rag.query", "rag.embedding", "rag.retrieval", "gen_ai.chat.completion");
// Verify parent-child relationships via span IDs
```

### Manual validation checklist:

After all sub-tasks are complete:
- [ ] Application starts with `langfuse.enabled=true` without errors
- [ ] Langfuse dashboard shows traces from `finstream-rag-audit` service
- [ ] Trace contains 4 nested spans: `rag.query` → `rag.embedding` → `rag.retrieval` → `gen_ai.chat.completion`
- [ ] Each LLM span has token usage attributes (`gen_ai.usage.input_tokens`, etc.)
- [ ] Each span has cost attribute (`gen_ai.usage.cost_usd`)
- [ ] Business context attributes appear (`finstream.audit.id`, etc.)
- [ ] Error cases show exception details and ERROR status
- [ ] Application starts normally with `langfuse.enabled=false` (no tracing, no errors)
- [ ] All existing tests still pass (tracing is non-intrusive)

---

## Critical Notes for Implementing Agent

1. **Do not modify core domain code.** All observability code goes in the infrastructure/adapter layer. If the project uses hexagonal architecture packages, create a new `observability` package under infrastructure/adapters.
2. **Adapt package names.** The package names used above (`com.finstream.infrastructure.observability`) are examples. Match the project's actual package structure.
3. **Check LangChain4j version.** `ChatModelListener` was introduced in LangChain4j 0.33+. If the project uses an older version, upgrade the LangChain4j BOM first.
4. **HTTP only, not gRPC.** Langfuse does not support gRPC for OTLP. Use `OtlpHttpSpanExporter`, not `OtlpGrpcSpanExporter`. The import is `io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter`.
5. **ThreadLocal cleanup.** Always clear `AuditTraceContext` in a `finally` block to prevent ThreadLocal leaks in servlet containers with thread reuse.
6. **Prices change.** The `ModelCostCalculator` prices are approximate as of early 2026. Verify current OpenAI pricing at https://openai.com/pricing.
7. **Spring AOP dependency.** Sub-task 5d requires `spring-boot-starter-aop`. If the project doesn't have it, add it. Alternatively, skip the AOP aspect and create the parent span manually in the service method.
8. **Graceful degradation.** The `@ConditionalOnProperty` on `LangfuseOtelConfig` ensures the entire tracing infrastructure is disabled when `langfuse.enabled=false`. The decorator pattern in Sub-task 5 means the base `ContentRetriever` and `EmbeddingModel` still work without tracing. The `LlmTracingListener` is only registered if the bean exists.
9. **ResourceAttributes deprecation.** If `ResourceAttributes.SERVICE_NAME` is deprecated in the OTel SDK version used, replace with `io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME` or the string literal `"service.name"`.
