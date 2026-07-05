package com.mockwise.mockwise_backend.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class BadRequestException extends AppException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, message);
    }

    public BadRequestException(String message, Map<String, Object> details) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, message, details);
    }
}
