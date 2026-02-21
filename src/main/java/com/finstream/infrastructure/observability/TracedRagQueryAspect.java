package com.finstream.infrastructure.observability;

import io.opentelemetry.api.OpenTelemetry;
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
 */
@Aspect
@Component
public class TracedRagQueryAspect {

    private final Tracer tracer;

    public TracedRagQueryAspect(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("finstream-rag-audit");
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
