package com.mockwise.mockwise_backend.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

/**
 * Base runtime exception for domain/API errors with HTTP status and error code.
 */
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final Map<String, Object> details;

    public AppException(HttpStatus status, ErrorCode code, String message) {
        this(status, code, message, null, null);
    }

    public AppException(HttpStatus status, ErrorCode code, String message, Throwable cause) {
        this(status, code, message, null, cause);
    }

    public AppException(HttpStatus status, ErrorCode code, String message, Map<String, Object> details) {
        this(status, code, message, details, null);
    }

    public AppException(HttpStatus status, ErrorCode code, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.details = details == null ? Collections.emptyMap() : Map.copyOf(details);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
