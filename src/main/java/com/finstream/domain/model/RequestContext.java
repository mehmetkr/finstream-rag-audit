package com.finstream.domain.model;

public class RequestContext {

    public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> USER_ID = ScopedValue.newInstance();

    private RequestContext() {}

    public static void runWithContext(String traceId, String tenantId, String userId,
                                      Runnable task) {
        ScopedValue.where(TRACE_ID, traceId)
                   .where(TENANT_ID, tenantId)
                   .where(USER_ID, userId)
                   .run(task);
    }
}
