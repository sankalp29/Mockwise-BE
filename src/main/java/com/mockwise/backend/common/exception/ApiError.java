package com.mockwise.backend.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<String> details,
        Map<String, Object> meta
) {
    public static ApiError of(int status, String error, ErrorCode code, String message, String path) {
        return new ApiError(Instant.now(), status, error, code.name(), message, path, null, null);
    }

    public static ApiError of(int status, String error, ErrorCode code, String message, String path,
                              List<String> details) {
        return new ApiError(Instant.now(), status, error, code.name(), message, path, details, null);
    }

    public static ApiError of(int status, String error, ErrorCode code, String message, String path,
                              List<String> details, Map<String, Object> meta) {
        return new ApiError(Instant.now(), status, error, code.name(), message, path, details, meta);
    }
}
