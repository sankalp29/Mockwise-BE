package com.mockwise.mockwise_backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps exceptions to a consistent {@link ApiError} response.
 * Controllers should throw domain exceptions (or let validation fail)
 * instead of catching and building error bodies ad hoc.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> handleAppException(AppException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Application error [{}]: {}", ex.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("Client error [{}]: {}", ex.getCode(), ex.getMessage());
        }
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        Map<String, Object> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        log.warn("Validation failed on {}: {}", request.getRequestURI(), fieldErrors);
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Request validation failed", request, Map.of("fields", fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        Map<String, Object> violations = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        v -> v.getMessage() != null ? v.getMessage() : "Invalid value",
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        log.warn("Constraint violation on {}: {}", request.getRequestURI(), violations);
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Request validation failed", request, Map.of("fields", violations));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex,
                                                      HttpServletRequest request) {
        log.warn("Malformed request body on {}: {}", request.getRequestURI(), rootMessage(ex));
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "Malformed or unreadable request body", request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
                                                       HttpServletRequest request) {
        log.warn("Missing request parameter '{}' on {}", ex.getParameterName(), request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST,
                "Missing required parameter: " + ex.getParameterName(), request,
                Map.of("parameter", ex.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        String name = ex.getName();
        String required = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        log.warn("Type mismatch for '{}' on {}", name, request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST,
                "Invalid value for parameter '" + name + "'; expected " + required,
                request, Map.of("parameter", name, "expectedType", required));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.BAD_REQUEST,
                "HTTP method not supported: " + ex.getMethod(), request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                "Endpoint not found", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                "You do not have permission to perform this action", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED,
                "Authentication is required", request, null);
    }

    /**
     * Legacy / third-party code may still throw these; map them sensibly.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST,
                safeClientMessage(ex.getMessage(), "Invalid request"), request, null);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiError> handleSecurityException(SecurityException ex, HttpServletRequest request) {
        log.warn("Security exception on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                safeClientMessage(ex.getMessage(), "Access denied"), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        // Never leak internal exception messages to clients
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Please try again later.", request, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, ErrorCode code, String message,
                                           HttpServletRequest request, Map<String, Object> details) {
        ApiError body = details == null || details.isEmpty()
                ? ApiError.of(status.value(), status.getReasonPhrase(), code, message, path(request))
                : ApiError.of(status.value(), status.getReasonPhrase(), code, message, path(request), details);
        return ResponseEntity.status(status).body(body);
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI();
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    private static String safeClientMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        // Avoid dumping stack-like or overly long messages
        return message.length() > 300 ? fallback : message;
    }
}
