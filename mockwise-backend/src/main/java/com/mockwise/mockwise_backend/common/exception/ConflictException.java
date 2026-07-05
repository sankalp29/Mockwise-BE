package com.mockwise.mockwise_backend.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }
}
