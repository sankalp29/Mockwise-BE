package com.mockwise.mockwise_backend.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthenticatedException extends AppException {

    public UnauthenticatedException(String message) {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, message);
    }

    public static UnauthenticatedException missing() {
        return new UnauthenticatedException("Authentication is required");
    }
}
