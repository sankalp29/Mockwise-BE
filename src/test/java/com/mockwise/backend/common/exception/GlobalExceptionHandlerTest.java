package com.mockwise.backend.common.exception;

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
        request.setRequestURI("/api/interview/start");
    }

    @Test
    void apiExceptionReturnsSafeClientMessageAndCode() {
        ResponseEntity<ApiError> response = handler.handleApiException(
                new BadRequestException("Difficulty is required."), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiError body = response.getBody();
        assertNotNull(body);
        assertEquals("Difficulty is required.", body.message());
        assertEquals(ErrorCode.BAD_REQUEST.name(), body.code());
        assertEquals("/api/interview/start", body.path());
        assertFalse(body.message().toLowerCase().contains("exception"));
    }

    @Test
    void notFoundExceptionMapsTo404() {
        ResponseEntity<ApiError> response = handler.handleApiException(
                ResourceNotFoundException.of("Interview"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Interview was not found.", response.getBody().message());
        assertEquals(ErrorCode.NOT_FOUND.name(), response.getBody().code());
    }

    @Test
    void forbiddenExceptionMapsTo403() {
        ResponseEntity<ApiError> response = handler.handleApiException(
                new ForbiddenException("You do not have access to this interview session."), request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(ErrorCode.FORBIDDEN.name(), response.getBody().code());
    }

    @Test
    void illegalArgumentWithSqlLikeMessageIsSanitized() {
        ResponseEntity<ApiError> response = handler.handleIllegalArgument(
                new IllegalArgumentException("SQLException: relation users does not exist"), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String msg = response.getBody().message().toLowerCase();
        assertFalse(msg.contains("sql"));
        assertFalse(msg.contains("relation"));
    }

    @Test
    void illegalArgumentNotFoundMapsTo404() {
        ResponseEntity<ApiError> response = handler.handleIllegalArgument(
                new IllegalArgumentException("Interview not found"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void genericExceptionDoesNotLeakInternals() {
        ResponseEntity<ApiError> response = handler.handleGeneric(
                new RuntimeException("Nested exception is org.hibernate.exception.SQLGrammarException"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        String msg = response.getBody().message().toLowerCase();
        assertFalse(msg.contains("hibernate"));
        assertFalse(msg.contains("sql"));
        assertTrue(msg.contains("try again") || msg.contains("went wrong"));
        assertEquals(ErrorCode.INTERNAL_ERROR.name(), response.getBody().code());
    }

    @Test
    void securityExceptionMapsToForbiddenWithoutRawMessage() {
        ResponseEntity<ApiError> response = handler.handleSecurity(
                new SecurityException("User id abc-secret leaked"), request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertFalse(response.getBody().message().contains("abc-secret"));
    }
}
