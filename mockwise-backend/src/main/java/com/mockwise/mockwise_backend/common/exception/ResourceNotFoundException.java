package com.mockwise.mockwise_backend.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String clientMessage) {
        super(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, clientMessage);
    }

    public static ResourceNotFoundException of(String resource) {
        return new ResourceNotFoundException(resource + " was not found.");
    }
}
