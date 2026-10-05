package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.service.FoodItemImageStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(FoodItemImageStorageException.class)
    public ResponseEntity<Map<String, Object>> handleFoodImage(FoodItemImageStorageException ex) {
        HttpStatus status = switch (ex.getType()) {
            case INVALID_FORMAT, INVALID_PATH -> HttpStatus.BAD_REQUEST;
            case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case STORAGE_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("type", ex.getType().name());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("type", ex.getStatus().name());
        body.put("code", ex.getCode());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /**
     * Everything the Keycloak layer throws deliberately: 401 on a bad credential, 409 on
     * a duplicate account, 502 when Keycloak is unreachable or misconfigured. Handling it
     * here writes the response directly instead of letting Spring Boot forward to /error.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getReason() != null ? ex.getReason() : status.getReasonPhrase());
        body.put("type", status.name());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("type", "BAD_REQUEST");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("type", "CONFLICT");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * A write that a database constraint refused and no service check caught first.
     * The SQL detail stays in the log; the client gets a plain conflict.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "This change conflicts with existing data: a duplicate value, or a record that is still in use.");
        body.put("type", "CONFLICT");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public ResponseEntity<Map<String, Object>> handleMultipart(Exception ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Upload too large.");
        body.put("type", "FILE_TOO_LARGE");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }
}
