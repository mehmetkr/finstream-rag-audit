package com.finstream.infrastructure.observability;

import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuditTraceContextTest {

    @AfterEach
    void cleanup() {
        AuditTraceContext.clear();
    }

    @Test
    void setAndGetCurrent() {
        var ctx = new AuditTraceContext("audit-1", "transaction", "rag");
        AuditTraceContext.setCurrent(ctx);

        assertThat(AuditTraceContext.getCurrent()).isSameAs(ctx);
    }

    @Test
    void getReturnsNullWhenNotSet() {
        assertThat(AuditTraceContext.getCurrent()).isNull();
    }

    @Test
    void clearRemovesContext() {
        AuditTraceContext.setCurrent(new AuditTraceContext("a", "b", "c"));
        AuditTraceContext.clear();

        assertThat(AuditTraceContext.getCurrent()).isNull();
    }

    @Test
    void applyToSpanSetsAllAttributes() {
        var ctx = new AuditTraceContext("audit-1", "transaction", "embedding");
        Span span = mock(Span.class);

        ctx.applyToSpan(span);

        verify(span).setAttribute("finstream.audit.id", "audit-1");
        verify(span).setAttribute("finstream.document.type", "transaction");
        verify(span).setAttribute("finstream.pipeline.stage", "embedding");
    }

    @Test
    void applyToSpanSkipsNullFields() {
        var ctx = new AuditTraceContext(null, null, null);
        Span span = mock(Span.class);

        ctx.applyToSpan(span);

        verifyNoInteractions(span);
    }

    @Test
    void inheritedByChildThread() throws Exception {
        var ctx = new AuditTraceContext("parent-audit", "tx", "eval");
        AuditTraceContext.setCurrent(ctx);

        var future = Thread.ofVirtual().start(() -> {
            assertThat(AuditTraceContext.getCurrent()).isSameAs(ctx);
        });
        future.join();
    }
}
