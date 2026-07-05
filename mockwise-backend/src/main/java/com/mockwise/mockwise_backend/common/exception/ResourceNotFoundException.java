package com.mockwise.mockwise_backend.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    public ResourceNotFoundException(String resourceType, Object id) {
        super(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                resourceType + " not found: " + id);
    }
}
