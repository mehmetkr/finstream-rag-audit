package com.finstream.domain.model;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestContextTest {

    @Test
    void scoped_values_should_be_readable_inside_scope() {
        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        AtomicReference<String> capturedTenantId = new AtomicReference<>();
        AtomicReference<String> capturedUserId = new AtomicReference<>();

        RequestContext.runWithContext("trace-123", "tenant-abc", "user-456", () -> {
            capturedTraceId.set(RequestContext.TRACE_ID.get());
            capturedTenantId.set(RequestContext.TENANT_ID.get());
            capturedUserId.set(RequestContext.USER_ID.get());
        });

        assertThat(capturedTraceId.get()).isEqualTo("trace-123");
        assertThat(capturedTenantId.get()).isEqualTo("tenant-abc");
        assertThat(capturedUserId.get()).isEqualTo("user-456");
    }

    @Test
    void scoped_values_should_be_unbound_outside_scope() {
        RequestContext.runWithContext("trace-123", "tenant-abc", "user-456", () -> {
            // inside scope — values are bound
        });

        // outside scope — values should be unbound
        assertThat(RequestContext.TRACE_ID.isBound()).isFalse();
        assertThat(RequestContext.TENANT_ID.isBound()).isFalse();
        assertThat(RequestContext.USER_ID.isBound()).isFalse();
    }

    @Test
    void accessing_unbound_scoped_value_should_throw() {
        assertThatThrownBy(() -> RequestContext.TRACE_ID.get())
                .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
