package com.finstream.infrastructure.observability;

import io.opentelemetry.api.trace.Span;

/**
 * Carries business context through the observability pipeline.
 * Stored in a ThreadLocal so all spans within a request can access it.
 *
 * IMPORTANT: Always call clear() in a finally block to prevent ThreadLocal leaks.
 *
 * Uses InheritableThreadLocal so virtual threads spawned via
 * CompletableFuture.supplyAsync inherit the context from their parent thread.
 */
public class AuditTraceContext {

    private static final InheritableThreadLocal<AuditTraceContext> CURRENT = new InheritableThreadLocal<>();

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
