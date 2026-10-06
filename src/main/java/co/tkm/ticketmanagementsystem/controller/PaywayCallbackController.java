package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.service.PaywayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** PayWay's server-to-server pushback ({@code return_url}). Public; trusted only by its signature. */
@RestController
@RequestMapping("/api/payway")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "ABA PayWay callback")
public class PaywayCallbackController {

    public static final String SIGNATURE_HEADER = "X-Payway-Hmac-Sha512";

    private final PaywayService paywayService;

    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "PayWay payment notification",
            description = "Verified with the X-Payway-Hmac-Sha512 header (401 INVALID_SIGNATURE otherwise). "
                    + "status \"0\" confirms the booking once; other statuses are only recorded.")
    public ResponseEntity<Map<String, Object>> callback(@RequestBody String body,
                                                        @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {
        paywayService.callback(body, signature);
        return ResponseEntity.ok(Map.of("received", true));
    }
}
