package com.finstream.infrastructure.adapters.web;

import com.finstream.domain.model.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScopedContextFilterTest {

    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;

    @InjectMocks ScopedContextFilter filter;

    @Test
    void should_generate_trace_id_if_missing() throws Exception {
        when(request.getHeader("X-Trace-Id")).thenReturn(null);

        doAnswer(invocation -> {
            assertThat(RequestContext.TRACE_ID.isBound()).isTrue();
            assertThat(RequestContext.TRACE_ID.get()).isNotNull();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);
    }

    @Test
    void should_propagate_trace_id_if_present() throws Exception {
        when(request.getHeader("X-Trace-Id")).thenReturn("existing-123");

        doAnswer(invocation -> {
            assertThat(RequestContext.TRACE_ID.get()).isEqualTo("existing-123");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);
    }

    @Test
    void should_wrap_checked_exceptions() throws Exception {
        when(request.getHeader("X-Trace-Id")).thenReturn("1");
        doThrow(new IOException("Network error")).when(chain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(IOException.class);
    }
}
