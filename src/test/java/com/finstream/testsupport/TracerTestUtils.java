package com.finstream.testsupport;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class TracerTestUtils {

    private TracerTestUtils() {
    }

    public static void stubTracer(Tracer tracer) {
        Span span = mock(Span.class);
        Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpan(any(Span.class))).thenReturn(scope);
    }

    public static void stubOpenTelemetry(OpenTelemetry openTelemetry) {
        Tracer tracer = mock(Tracer.class);
        when(openTelemetry.getTracer(anyString())).thenReturn(tracer);
        stubTracer(tracer);
    }
}
