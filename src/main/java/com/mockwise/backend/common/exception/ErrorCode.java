package com.mockwise.backend.common.exception;

/**
 * Stable machine-readable error codes for clients.
 * Messages stay user-facing; codes stay stable for UI mapping.
 */
public enum ErrorCode {
    VALIDATION_ERROR,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    GONE,
    METHOD_NOT_ALLOWED,
    UNSUPPORTED_MEDIA_TYPE,
    PAYLOAD_TOO_LARGE,
    RATE_LIMITED,
    INTERNAL_ERROR,
    SERVICE_UNAVAILABLE
}
