package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.service.FoodItemImageStorageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

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
        body.put("type", "SERVER_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public ResponseEntity<Map<String, Object>> handleMultipart(Exception ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Upload too large.");
        body.put("type", "FILE_TOO_LARGE");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }
}
