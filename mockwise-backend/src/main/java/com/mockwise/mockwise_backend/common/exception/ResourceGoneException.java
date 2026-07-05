package com.mockwise.mockwise_backend.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Resource existed but is no longer available (e.g. expired interview session).
 */
public class ResourceGoneException extends AppException {

    public ResourceGoneException(String message) {
        super(HttpStatus.GONE, ErrorCode.GONE, message);
    }

    public ResourceGoneException(String message, Map<String, Object> details) {
        super(HttpStatus.GONE, ErrorCode.GONE, message, details);
    }
}
