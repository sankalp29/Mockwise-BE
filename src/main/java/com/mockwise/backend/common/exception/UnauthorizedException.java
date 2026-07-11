package com.mockwise.backend.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String clientMessage) {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, clientMessage);
    }

    public UnauthorizedException() {
        this("Authentication is required to access this resource.");
    }
}
