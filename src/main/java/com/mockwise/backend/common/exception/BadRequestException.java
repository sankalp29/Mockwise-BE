package com.mockwise.backend.common.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {
    public BadRequestException(String clientMessage) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, clientMessage);
    }

    public BadRequestException(ErrorCode code, String clientMessage) {
        super(HttpStatus.BAD_REQUEST, code, clientMessage);
    }
}
