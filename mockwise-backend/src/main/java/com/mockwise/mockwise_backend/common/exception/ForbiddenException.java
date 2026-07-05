package com.mockwise.mockwise_backend.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends AppException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, message);
    }
}
