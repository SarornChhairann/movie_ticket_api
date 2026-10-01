package co.tkm.ticketmanagementsystem.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Resolves who owns a seat hold. The API is a stateless JWT resource server, so
 * clients identify their hold with an {@code X-Session-Id} header (a UUID they
 * create once per app run). Requests without the header fall back to the servlet
 * session id, which keeps cookie-based browser clients working.
 */
public final class HoldSessions {

    public static final String HEADER = "X-Session-Id";
    public static final String SESSION_REQUIRED = "SESSION_REQUIRED";

    private HoldSessions() {
    }

    public static String resolve(String header, HttpServletRequest request) {
        if (header == null || header.isBlank()) {
            return request.getSession(true).getId();
        }
        String value = header.trim();
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, SESSION_REQUIRED,
                    HEADER + " must be a UUID.");
        }
        return value;
    }
}
