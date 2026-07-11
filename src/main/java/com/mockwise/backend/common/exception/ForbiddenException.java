package com.mockwise.backend.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {
    public ForbiddenException(String clientMessage) {
        super(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, clientMessage);
    }

    public ForbiddenException() {
        this("You do not have permission to perform this action.");
    }
}
