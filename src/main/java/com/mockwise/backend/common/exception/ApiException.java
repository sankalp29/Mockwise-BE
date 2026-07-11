package com.mockwise.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base API exception. Carries a safe client message and HTTP status.
 * Never put stack traces, SQL, or internal class names in {@code clientMessage}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final String clientMessage;

    public ApiException(HttpStatus status, ErrorCode code, String clientMessage) {
        super(clientMessage);
        this.status = status;
        this.code = code;
        this.clientMessage = clientMessage;
    }

    public ApiException(HttpStatus status, ErrorCode code, String clientMessage, Throwable cause) {
        super(clientMessage, cause);
        this.status = status;
        this.code = code;
        this.clientMessage = clientMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getClientMessage() {
        return clientMessage;
    }
}
