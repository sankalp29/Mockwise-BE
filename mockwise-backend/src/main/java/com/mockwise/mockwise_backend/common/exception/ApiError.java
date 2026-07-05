package com.mockwise.mockwise_backend.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standard API error body returned for failed requests.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path,
        Map<String, Object> details
) {
    public static ApiError of(int status, String error, ErrorCode code, String message, String path) {
        return new ApiError(Instant.now(), status, error, code, message, path, null);
    }

    public static ApiError of(int status, String error, ErrorCode code, String message, String path,
                              Map<String, Object> details) {
        return new ApiError(Instant.now(), status, error, code, message, path, details);
    }
}
