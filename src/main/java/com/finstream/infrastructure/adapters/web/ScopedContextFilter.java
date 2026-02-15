package com.finstream.infrastructure.adapters.web;

import com.finstream.domain.model.RequestContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScopedContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String traceId = httpRequest.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        String tenantId = httpRequest.getHeader("X-Tenant-Id");
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }

        String userId = httpRequest.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            userId = "anonymous";
        }

        final String finalTraceId = traceId;
        final String finalTenantId = tenantId;
        final String finalUserId = userId;

        // ScopedValue.where(...).run() accepts a Runnable, which cannot throw checked exceptions.
        // FilterChain.doFilter() declares IOException and ServletException, so we must catch and
        // wrap them. Spring's error handling infrastructure will unwrap these if they propagate.
        ScopedValue.where(RequestContext.TRACE_ID, finalTraceId)
                   .where(RequestContext.TENANT_ID, finalTenantId)
                   .where(RequestContext.USER_ID, finalUserId)
                   .run(() -> {
                       try {
                           chain.doFilter(request, response);
                       } catch (IOException e) {
                           throw new java.io.UncheckedIOException(e);
                       } catch (ServletException e) {
                           throw new RuntimeException(e);
                       }
                   });
    }
}
