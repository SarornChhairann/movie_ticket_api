package co.tkm.ticketmanagementsystem.controller;

import org.springframework.http.HttpStatus;

/**
 * A domain error with a stable {@code code} clients can branch on instead of the
 * message text (e.g. {@code ACCOUNT_BLOCKED}, {@code SEAT_HELD}). Rendered by
 * {@link ApiExceptionHandler} as {@code {error, type, code}}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
