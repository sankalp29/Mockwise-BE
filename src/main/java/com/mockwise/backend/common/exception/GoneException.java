package com.mockwise.backend.common.exception;

import org.springframework.http.HttpStatus;

public class GoneException extends ApiException {
    public GoneException(String clientMessage) {
        super(HttpStatus.GONE, ErrorCode.GONE, clientMessage);
    }
}
