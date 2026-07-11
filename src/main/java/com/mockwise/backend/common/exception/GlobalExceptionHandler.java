package com.mockwise.backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Central API error mapping. Client responses never include stack traces,
 * SQL, class names, or other internal details. Full detail is logged server-side.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String GENERIC_INTERNAL =
            "Something went wrong while processing your request. Please try again later.";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        log.warn("API exception [{}] {}: {}", ex.getCode(), request.getRequestURI(), ex.getClientMessage());
        return build(ex.getStatus(), ex.getStatus().getReasonPhrase(), ex.getCode(),
                ex.getClientMessage(), request, null);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class
    })
    public ResponseEntity<ApiError> handleValidation(Exception ex, HttpServletRequest request) {
        List<String> details;
        if (ex instanceof MethodArgumentNotValidException manv) {
            details = manv.getBindingResult().getFieldErrors().stream()
                    .map(fe -> safeField(fe.getField()) + ": " + safeMessage(fe.getDefaultMessage()))
                    .collect(Collectors.toList());
        } else {
            BindException be = (BindException) ex;
            details = be.getBindingResult().getFieldErrors().stream()
                    .map(fe -> safeField(fe.getField()) + ": " + safeMessage(fe.getDefaultMessage()))
                    .collect(Collectors.toList());
        }
        log.warn("Validation failed on {}: {}", request.getRequestURI(), details);
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ErrorCode.VALIDATION_ERROR,
                "One or more fields are invalid.", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> safeMessage(v.getMessage()))
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ErrorCode.VALIDATION_ERROR,
                "One or more request parameters are invalid.", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        log.warn("Unreadable body on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ErrorCode.BAD_REQUEST,
                "Request body is missing or malformed.", request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ErrorCode.VALIDATION_ERROR,
                "Required parameter '" + ex.getParameterName() + "' is missing.", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        String name = ex.getName() != null ? ex.getName() : "parameter";
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ErrorCode.VALIDATION_ERROR,
                "Parameter '" + name + "' has an invalid value.", request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", ErrorCode.METHOD_NOT_ALLOWED,
                "HTTP method is not supported for this endpoint.", request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException ex,
                                                    HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type",
                ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Content type is not supported. Use application/json where applicable.", request, null);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ErrorCode.NOT_FOUND,
                "The requested resource was not found.", request, null);
    }

    @ExceptionHandler({
            AuthenticationException.class,
            AuthenticationCredentialsNotFoundException.class
    })
    public ResponseEntity<ApiError> handleAuth(Exception ex, HttpServletRequest request) {
        log.warn("Authentication failure on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", ErrorCode.UNAUTHORIZED,
                "Authentication is required or the provided credentials are invalid.", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Forbidden", ErrorCode.FORBIDDEN,
                "You do not have permission to access this resource.", request, null);
    }

    /** Legacy service throws — map without leaking raw messages when unsafe. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex,
                                                          HttpServletRequest request) {
        String raw = ex.getMessage() != null ? ex.getMessage() : "";
        log.warn("IllegalArgument on {}: {}", request.getRequestURI(), raw);

        // Sanitize first so SQL/internal text never becomes a 404 "not found" leak vector
        if (containsInternalLeakMarkers(raw)) {
            return build(HttpStatus.BAD_REQUEST, "Bad Request", ErrorCode.BAD_REQUEST,
                    "The request could not be processed.", request, null);
        }
        if (looksLikeNotFound(raw)) {
            return build(HttpStatus.NOT_FOUND, "Not Found", ErrorCode.NOT_FOUND,
                    sanitizeLegacyMessage(raw, "The requested resource was not found."), request, null);
        }
        if (looksLikeAuth(raw)) {
            return build(HttpStatus.UNAUTHORIZED, "Unauthorized", ErrorCode.UNAUTHORIZED,
                    "Authentication is required to access this resource.", request, null);
        }
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ErrorCode.BAD_REQUEST,
                sanitizeLegacyMessage(raw, "The request could not be processed."), request, null);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiError> handleSecurity(SecurityException ex, HttpServletRequest request) {
        log.warn("SecurityException on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Forbidden", ErrorCode.FORBIDDEN,
                "You do not have permission to access this resource.", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.error("Data integrity violation on {}", request.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT, "Conflict", ErrorCode.CONFLICT,
                "The request conflicts with the current state of the resource.", request, null);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        log.error("Data access error on {}", request.getRequestURI(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ErrorCode.SERVICE_UNAVAILABLE,
                "A data service is temporarily unavailable. Please try again later.", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ErrorCode.INTERNAL_ERROR,
                GENERIC_INTERNAL, request, null);
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String error, ErrorCode code,
                                                  String message, HttpServletRequest request,
                                                  List<String> details) {
        ApiError body = ApiError.of(status.value(), error, code, message,
                request != null ? request.getRequestURI() : null, details);
        return ResponseEntity.status(status).body(body);
    }

    private static boolean looksLikeNotFound(String raw) {
        String m = raw.toLowerCase();
        return m.contains("not found");
    }

    private static boolean looksLikeAuth(String raw) {
        String m = raw.toLowerCase();
        return m.contains("no authentication") || m.contains("invalid authentication")
                || m.contains("unauthorized") || m.contains("not authenticated");
    }

    private static boolean containsInternalLeakMarkers(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String lower = raw.toLowerCase();
        return lower.contains("sql") || lower.contains("jdbc") || lower.contains("hibernate")
                || lower.contains("exception") || lower.contains("stack")
                || lower.contains("com.") || lower.contains("org.")
                || lower.contains("null pointer") || lower.contains("nested exception")
                || raw.contains("\n") || raw.contains("\tat ");
    }

    /**
     * Allow known safe business messages; strip internals (SQL, packages, stack-ish content).
     */
    private static String sanitizeLegacyMessage(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String m = raw.trim();
        if (m.length() > 200) {
            return fallback;
        }
        if (containsInternalLeakMarkers(m)) {
            return fallback;
        }
        // Strip class simple-name leaks like "Invalid authentication type: String"
        if (m.toLowerCase().startsWith("invalid authentication type")) {
            return "Authentication is required to access this resource.";
        }
        return m;
    }

    private static String safeField(String field) {
        if (field == null || field.isBlank()) {
            return "field";
        }
        return field.replaceAll("[^a-zA-Z0-9_.\\[\\]]", "");
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "invalid";
        }
        String m = message.trim();
        if (m.length() > 120) {
            return m.substring(0, 120);
        }
        return m;
    }
}
