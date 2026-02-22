package com.finstream.testsupport;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class TracerTestUtils {

    private TracerTestUtils() {
    }

    /**
     * Stubs a Micrometer Tracer with mocked Span and SpanInScope.
     * Used by tests that inject io.micrometer.tracing.Tracer.
     */
    public static void stubTracer(Tracer tracer) {
        Span span = mock(Span.class);
        Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpan(any(Span.class))).thenReturn(scope);
    }
}
