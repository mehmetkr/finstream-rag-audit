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
