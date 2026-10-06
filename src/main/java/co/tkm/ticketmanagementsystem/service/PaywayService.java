package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.config.PaywayProperties;
import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.dto.CheckoutData;
import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.enums.PaymentStatus;
import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ABA PayWay KHQR checkout (sprint 9). The database work lives in {@link BookingService}
 * transactions; this class makes the PayWay HTTP calls between them so no seat lock is
 * held while PayWay answers.
 */
@Service
@RequiredArgsConstructor
public class PaywayService {

    private static final Logger log = LoggerFactory.getLogger(PaywayService.class);

    public static final String PAYWAY_UNAVAILABLE = "PAYWAY_UNAVAILABLE";
    public static final String INVALID_SIGNATURE = "INVALID_SIGNATURE";
    public static final String PAYMENT_OPTION = "abapay_khqr_deeplink";
    public static final String RETURN_SCHEME = "cinekh://payway/return";

    static final String APPROVED = "APPROVED";
    static final String PENDING = "PENDING";
    static final String DECLINED = "DECLINED";
    static final String CANCELLED = "CANCELLED";

    private static final DateTimeFormatter REQ_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BookingService bookingService;
    private final PaywayClient paywayClient;
    private final PaywayProperties properties;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    /** What the app needs to open the checkout page. */
    public record Started(Long bookingId, String reference, String tranId, BigDecimal amount, String checkoutUrl,
                          String abaDeeplink) {
    }

    /** One poll answer: {@code APPROVED} carries the confirmation, the rest carry nothing. */
    public record Status(String status, BookingService.Confirmation confirmation) {
        static Status of(String status) {
            return new Status(status, null);
        }
    }

    public Started start(Long showtimeId, List<Long> seatIds, String userEmail, String sessionId,
                         String guestName, String guestPhone, String guestEmail, String promoCode,
                         Map<Long, Integer> foodSelections) {
        if (!paywayClient.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, PAYWAY_UNAVAILABLE,
                    "ABA PayWay is not configured on this server.");
        }
        BookingService.PaywayBooking pending = bookingService.openPaywayBooking(showtimeId, seatIds, userEmail,
                sessionId, guestName, guestPhone, guestEmail, promoCode, foodSelections);
        pending.supersededTranIds().forEach(this::closeAtPayway);

        PaywayClient.PurchaseResult result;
        try {
            result = paywayClient.purchase(purchaseFields(pending, reqTime()));
        } catch (PaywayClient.PaywayException e) {
            // Booking CANCELLED, payment FAILED; the hold is kept so the customer can retry.
            bookingService.cancelPayway(pending.tranId(), "PURCHASE_FAILED");
            throw new PaymentFailedException(e.getMessage());
        }
        Booking booking = pending.booking();
        return new Started(booking.getId(), booking.getReference(), pending.tranId(), booking.getTotalAmount(),
                result.checkoutUrl(), result.abaDeeplink());
    }

    /** Poll: asks PayWay (Check transaction) while the payment is still pending here. */
    public Status poll(String tranId, String userEmail, String sessionId) {
        BookingService.PaywayState state = requireOwner(tranId, userEmail, sessionId);
        Status settled = settled(tranId, state);
        if (settled != null) return settled;

        String providerStatus;
        try {
            providerStatus = paywayClient.checkTransaction(tranId);
        } catch (PaywayClient.PaywayException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, PAYWAY_UNAVAILABLE, e.getMessage());
        }
        return apply(tranId, providerStatus);
    }

    /**
     * The customer left the checkout page while the payment was pending. A payment that
     * went through in the meantime still completes; otherwise PayWay closes it so it can
     * no longer be paid, and the booking is cancelled with the seats still available.
     */
    public Status close(String tranId, String userEmail, String sessionId) {
        BookingService.PaywayState state = requireOwner(tranId, userEmail, sessionId);
        Status settled = settled(tranId, state);
        if (settled != null) return settled;
        return closeAndSettle(tranId, "CLOSED");
    }

    /** Hold expired before PayWay approved: close every such payment. */
    @Scheduled(fixedDelay = 60_000)
    public void closeExpired() {
        for (String tranId : paymentRepository.findExpiredTranIds(PaymentStatus.PENDING, LocalDateTime.now())) {
            try {
                closeAndSettle(tranId, "EXPIRED");
            } catch (RuntimeException e) {
                log.warn("Could not close expired PayWay payment {}: {}", tranId, e.getMessage());
            }
        }
    }

    /**
     * PayWay's pushback to {@code return_url}. The signature header must match the
     * sorted-keys HMAC of the body, or nothing changes. Status {@code "0"} completes the
     * booking exactly like an {@code APPROVED} poll; anything else is only stored.
     */
    public void callback(String rawBody, String signature) {
        if (!properties.isConfigured()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_SIGNATURE, "PayWay is not configured.");
        }
        JsonNode body;
        try {
            body = objectMapper.readTree(rawBody == null ? "" : rawBody);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Callback body is not JSON.");
        }
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("Callback body is not a JSON object.");
        }
        String expected = PaywaySigner.callbackSignature(body, properties.apiKey());
        if (!PaywaySigner.matches(expected, signature)) {
            log.warn("PayWay callback rejected: bad signature");
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_SIGNATURE, "Invalid signature.");
        }

        String tranId = body.path("tran_id").asText("");
        String status = body.path("status").asText("");
        if (tranId.isEmpty()) {
            throw new IllegalArgumentException("Callback has no tran_id.");
        }
        if ("0".equals(status)) {
            try {
                bookingService.completePayway(tranId);
            } catch (ApiException e) {
                log.warn("PayWay callback for {} not applied: {}", tranId, e.getCode());
            }
        } else {
            bookingService.recordPaywayStatus(tranId, "CALLBACK_" + status);
        }
    }

    // ------------------------------------------------------------------ helpers

    private Status closeAndSettle(String tranId, String reason) {
        String providerStatus = checkQuietly(tranId);
        if (APPROVED.equals(providerStatus)) {
            return completed(tranId);
        }
        if (!closeAtPayway(tranId) && APPROVED.equals(checkQuietly(tranId))) {
            return completed(tranId);
        }
        bookingService.cancelPayway(tranId, reason);
        return Status.of(CANCELLED);
    }

    private Status apply(String tranId, String providerStatus) {
        return switch (providerStatus) {
            case APPROVED -> completed(tranId);
            case DECLINED, CANCELLED -> {
                bookingService.cancelPayway(tranId, providerStatus);
                closeAtPayway(tranId);
                yield Status.of(providerStatus);
            }
            default -> Status.of(PENDING);
        };
    }

    private Status completed(String tranId) {
        try {
            return new Status(APPROVED, bookingService.completePayway(tranId));
        } catch (ApiException e) {
            if (BookingService.PAYMENT_CLOSED.equals(e.getCode())) return Status.of(CANCELLED);
            throw e;
        }
    }

    /** The local answer when the payment is no longer pending, else null. */
    private Status settled(String tranId, BookingService.PaywayState state) {
        if (state.bookingStatus() == BookingStatus.CONFIRMED) {
            return completed(tranId);
        }
        if (state.bookingStatus() == BookingStatus.CANCELLED) {
            return Status.of(DECLINED.equals(state.providerStatus()) ? DECLINED : CANCELLED);
        }
        return null;
    }

    private BookingService.PaywayState requireOwner(String tranId, String userEmail, String sessionId) {
        BookingService.PaywayState state = bookingService.paywayState(tranId);
        // The signed-in owner, or the session that started it (G31): a guest app restarted
        // mid-payment restores that session, and a user whose sign-in lapsed still has it.
        boolean owner = (state.ownerEmail() != null
                        && state.ownerEmail().equalsIgnoreCase(Objects.requireNonNullElse(userEmail, "")))
                || (state.sessionId() != null && state.sessionId().equals(sessionId));
        if (!owner) {
            throw new ApiException(HttpStatus.FORBIDDEN, BookingService.NOT_OWNER,
                    "This payment belongs to someone else.");
        }
        return state;
    }

    private String checkQuietly(String tranId) {
        try {
            return paywayClient.checkTransaction(tranId);
        } catch (RuntimeException e) {
            log.warn("PayWay check {} failed: {}", tranId, e.getMessage());
            return PENDING;
        }
    }

    private boolean closeAtPayway(String tranId) {
        try {
            return paywayClient.closeTransaction(tranId);
        } catch (RuntimeException e) {
            log.warn("PayWay close {} failed: {}", tranId, e.getMessage());
            return false;
        }
    }

    /** UTC {@code YYYYMMDDHHmmss}. */
    static String reqTime() {
        return LocalDateTime.now(ZoneOffset.UTC).format(REQ_TIME);
    }

    /** Purchase form fields for [pending], without {@code merchant_id} and {@code hash} (the client adds both). */
    Map<String, String> purchaseFields(BookingService.PaywayBooking pending, String reqTime) {
        Booking booking = pending.booking();
        User user = booking.getUser();
        String name = user != null ? user.getName() : booking.getGuestName();
        String[] names = splitName(name);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("req_time", reqTime);
        fields.put("tran_id", pending.tranId());
        fields.put("amount", booking.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        fields.put("items", base64(items(pending.checkoutData())));
        fields.put("shipping", "0");
        fields.put("firstname", names[0]);
        fields.put("lastname", names[1]);
        fields.put("email", nullToEmpty(user != null ? user.getEmail() : booking.getGuestEmail()));
        fields.put("phone", nullToEmpty(user != null ? user.getPhone() : booking.getGuestPhone()));
        fields.put("type", "purchase");
        fields.put("payment_option", PAYMENT_OPTION);
        fields.put("return_url", isBlank(properties.returnUrl()) ? "" : base64(properties.returnUrl()));
        fields.put("return_deeplink", base64(
                "{\"ios_scheme\":\"" + RETURN_SCHEME + "\",\"android_scheme\":\"" + RETURN_SCHEME + "\"}"));
        fields.put("currency", "USD");
        fields.put("lifetime", String.valueOf(pending.lifetimeMinutes()));
        fields.put("skip_success_page", "1");
        fields.put("payment_gate", "0");
        fields.put("view_type", "hosted_view");
        return fields;
    }

    /** Seat and food lines for PayWay's description only; PayWay never prices them. */
    private String items(CheckoutData data) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (ShowtimeSeat seat : data.getSelectedSeats()) {
            boolean couple = seat.getSeat().getSeatType() == SeatType.COUPLE;
            BigDecimal price = couple
                    ? data.getShowtime().getCouplePrice().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
                    : data.getShowtime().getNormalPrice();
            lines.add(line("Seat " + seat.getSeat().getSeatCode(), 1, price));
        }
        for (CheckoutData.FoodLine food : data.getFoodLines()) {
            lines.add(line(food.name(), food.quantity(), food.unitPrice()));
        }
        try {
            return objectMapper.writeValueAsString(lines);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> line(String name, int quantity, BigDecimal price) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("name", name);
        line.put("quantity", quantity);
        line.put("price", price);
        return line;
    }

    /** First token is the first name; the rest is the last name, or {@code -} when there is none. */
    static String[] splitName(String name) {
        String trimmed = name == null ? "" : name.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[]{trimmed, "-"};
        }
        String last = trimmed.substring(space + 1).trim();
        return new String[]{trimmed.substring(0, space), last.isEmpty() ? "-" : last};
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
