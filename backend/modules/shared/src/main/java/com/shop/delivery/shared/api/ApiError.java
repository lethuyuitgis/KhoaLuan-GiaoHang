package com.shop.delivery.shared.api;

import java.time.Instant;
import java.util.List;

/**
 * Chuẩn response body cho mọi error. Match với spec section 11.3.
 */
public record ApiError(
    String code,
    String message,
    String traceId,
    Instant timestamp,
    List<FieldError> errors
) {

    public ApiError(String code, String message, String traceId) {
        this(code, message, traceId, Instant.now(), null);
    }

    public ApiError(String code, String message, String traceId, List<FieldError> errors) {
        this(code, message, traceId, Instant.now(), errors);
    }

    public record FieldError(
        String field,
        String message
    ) {
    }
}
