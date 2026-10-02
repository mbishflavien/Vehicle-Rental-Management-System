package com.vrms.exception;

import org.springframework.http.HttpStatus;

/** Business-rule failure that maps directly to an HTTP status and a user-facing message. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    /** Form field the error belongs to (so the UI can show it under that input), or null. */
    private final String field;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, String field) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, what + " not found");
    }

    public static ApiException conflict(String message, String field) {
        return new ApiException(HttpStatus.CONFLICT, message, field);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public HttpStatus getStatus() { return status; }
    public String getField() { return field; }
}
