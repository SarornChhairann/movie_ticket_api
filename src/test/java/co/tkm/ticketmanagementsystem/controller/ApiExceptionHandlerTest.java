package co.tkm.ticketmanagementsystem.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void apiException_writesStatusAndCode() {
        ResponseEntity<Map<String, Object>> response = handler.handleApi(
                new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_BLOCKED", "This account is suspended."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody())
                .containsEntry("error", "This account is suspended.")
                .containsEntry("type", "FORBIDDEN")
                .containsEntry("code", "ACCOUNT_BLOCKED");
    }

    @Test
    void illegalArgument_keepsTheOldShapeWithoutCode() {
        ResponseEntity<Map<String, Object>> response = handler.handleBadRequest(
                new IllegalArgumentException("Please select at least one seat."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .containsEntry("error", "Please select at least one seat.")
                .containsEntry("type", "BAD_REQUEST")
                .doesNotContainKey("code");
    }
}
