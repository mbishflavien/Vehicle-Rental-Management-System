package com.vrms.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error leaves the API in the same shape:
 * { "status": 400, "message": "...", "fieldErrors": { "plateNumber": "..." }, "timestamp": "..." }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        Map<String, String> fields = ex.getField() == null ? Map.of() : Map.of(ex.getField(), ex.getMessage());
        return body(ex.getStatus(), ex.getMessage(), fields);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return body(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "Request body is missing or has an invalid value", Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return body(HttpStatus.CONFLICT, "This record conflicts with existing data (duplicate or still in use)", Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleDenied(AccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, "You do not have permission to do that", Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(AuthenticationException ex) {
        return body(HttpStatus.UNAUTHORIZED, "Invalid email or password", Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return body(HttpStatus.NOT_FOUND, "Not found", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        log.error("Unhandled error", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side", Map.of());
    }

    public static Map<String, Object> errorBody(HttpStatus status, String message, Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message);
        body.put("fieldErrors", fields);
        body.put("timestamp", Instant.now().toString());
        return body;
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(errorBody(status, message, fields));
    }
}
