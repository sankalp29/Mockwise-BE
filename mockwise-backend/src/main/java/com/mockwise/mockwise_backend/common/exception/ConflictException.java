package com.mockwise.mockwise_backend.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
    public ConflictException(String clientMessage) {
        super(HttpStatus.CONFLICT, ErrorCode.CONFLICT, clientMessage);
    }
}
