package com.mockwise.mockwise_backend.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/interview/test");
    }

    @Test
    void appExceptionMapsStatusAndCode() {
        ResponseEntity<ApiError> response = handler.handleAppException(
                new ResourceNotFoundException("Interview", "abc"),
                request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.NOT_FOUND, response.getBody().code());
        assertEquals("/api/interview/test", response.getBody().path());
        assertTrue(response.getBody().message().contains("Interview"));
    }

    @Test
    void forbiddenExceptionReturns403() {
        ResponseEntity<ApiError> response = handler.handleAppException(
                new ForbiddenException("You do not have access to this interview session"),
                request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(ErrorCode.FORBIDDEN, response.getBody().code());
    }

    @Test
    void unauthenticatedExceptionReturns401() {
        ResponseEntity<ApiError> response = handler.handleAppException(
                UnauthenticatedException.missing(),
                request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ErrorCode.UNAUTHENTICATED, response.getBody().code());
    }

    @Test
    void unexpectedExceptionDoesNotLeakMessage() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new RuntimeException("secret db password is hunter2"),
                request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ErrorCode.INTERNAL_ERROR, response.getBody().code());
        assertFalse(response.getBody().message().contains("hunter2"));
        assertTrue(response.getBody().message().toLowerCase().contains("unexpected"));
    }

    @Test
    void illegalArgumentMapsToBadRequest() {
        ResponseEntity<ApiError> response = handler.handleIllegalArgument(
                new IllegalArgumentException("bad input"),
                request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCode.BAD_REQUEST, response.getBody().code());
        assertEquals("bad input", response.getBody().message());
    }

    @Test
    void resourceGoneIncludesDetails() {
        ResponseEntity<ApiError> response = handler.handleAppException(
                new ResourceGoneException("Interview has ended", java.util.Map.of("expired", true)),
                request);

        assertEquals(HttpStatus.GONE, response.getStatusCode());
        assertEquals(ErrorCode.GONE, response.getBody().code());
        assertEquals(true, response.getBody().details().get("expired"));
    }
}
