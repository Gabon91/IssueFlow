package com.att.tdp.issueflow.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * RFC-7807-style error envelope returned by {@link GlobalExceptionHandler}.
 * Shape matches the {@code ApiError} schema in {@code openapi.yaml}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    String traceId,
    List<ApiFieldError> fieldErrors
) {
    public record ApiFieldError(String field, String message) {}
}
