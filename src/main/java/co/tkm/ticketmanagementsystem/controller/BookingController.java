package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.dto.CheckoutData;
import co.tkm.ticketmanagementsystem.enums.PaymentStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.FoodItem;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.service.BookingService;
import co.tkm.ticketmanagementsystem.service.LookupRateLimiter;
import co.tkm.ticketmanagementsystem.service.PaywayService;
import co.tkm.ticketmanagementsystem.service.PromoCodeService;
import co.tkm.ticketmanagementsystem.service.SeatHoldService;
import co.tkm.ticketmanagementsystem.service.SeatRealtimeService;
import co.tkm.ticketmanagementsystem.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Seat map, checkout, confirmation, and booking history")
public class BookingController {

    private final BookingService bookingService;
    private final TicketService ticketService;
    private final SeatHoldService seatHoldService;
    private final SeatRealtimeService seatRealtimeService;
    private final PromoCodeService promoCodeService;
    private final PaywayService paywayService;
    private final LookupRateLimiter lookupRateLimiter;

    public static final String TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS";

    @GetMapping(value = "/seats/{showtimeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the seat map for a showtime, including which seats are held")
    @Transactional(readOnly = true)
    public ResponseEntity<SeatMapResponse> seatMap(@PathVariable Long showtimeId,
                                                   @RequestHeader(value = HoldSessions.HEADER, required = false) String sessionHeader,
                                                   HttpServletRequest request) {
        List<ShowtimeSeat> showtimeSeats = bookingService.getSeatsForShowtime(showtimeId);
        String sessionId = HoldSessions.resolve(sessionHeader, request);
        Set<Long> heldByOthers = seatHoldService.getHeldSeatIdsByOtherSessionsForShowtime(showtimeId, sessionId);
        Set<Long> heldByMe = seatHoldService.getHeldSeatIdsBySession(sessionId);
        Showtime showtime = bookingService.getShowtime(showtimeId);
        DayOfWeek dow = showtime.getShowDate().getDayOfWeek();
        boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        boolean isMorning = showtime.getShowTime() != null
                && showtime.getShowTime().isBefore(LocalTime.NOON);

        List<SeatInfo> seats = showtimeSeats.stream().map(this::toSeatInfo).toList();

        return ResponseEntity.ok(new SeatMapResponse(
                showtimeId, toShowtimeInfo(showtime), seats, heldByOthers, heldByMe,
                sessionId, seatHoldService.getHoldMinutes(), isWeekend, isMorning, seatHoldService.getMaxSeats()));
    }

    @GetMapping(value = "/checkout/{showtimeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Preview checkout totals for the selected seats and (optionally) a promo code; places a temporary hold on the seats")
    public ResponseEntity<CheckoutResponse> checkoutPreview(@PathVariable Long showtimeId,
                                  @RequestParam("seatIds") List<Long> seatIds,
                                  @RequestParam(value = "promoCode", required = false) String promoCode,
                                  Authentication authentication,
                                  @RequestHeader(value = HoldSessions.HEADER, required = false) String sessionHeader,
                                  HttpServletRequest request) {
        String userEmail = resolveEmail(authentication);
        String sessionId = HoldSessions.resolve(sessionHeader, request);
        if (seatHoldService.hasActiveConflict(seatIds, sessionId)) {
            throw new ApiException(HttpStatus.CONFLICT, SeatHoldService.SEAT_HELD,
                    "Some seats are currently held by another user. Please reselect.");
        }
        CheckoutData checkoutData = bookingService.buildCheckout(showtimeId, seatIds, userEmail, promoCode);
        seatHoldService.holdSeatsRealtime(showtimeId, seatIds, sessionId);
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());

        return ResponseEntity.ok(toCheckoutResponse(checkoutData, seatIds, isAuthenticated));
    }

    @PostMapping(value = "/quote/{showtimeId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Price the held seats with optional promo and food; read-only (no hold, no booking)")
    @Transactional(readOnly = true)
    public ResponseEntity<QuoteResponse> quote(@PathVariable Long showtimeId,
                                               @RequestBody QuoteRequest req,
                                               Authentication authentication,
                                               @RequestHeader(value = HoldSessions.HEADER, required = false) String sessionHeader,
                                               HttpServletRequest request) {
        String sessionId = HoldSessions.resolve(sessionHeader, request);
        List<Long> seatIds = req.seatIds() == null ? List.of() : req.seatIds();
        if (!seatHoldService.ownsActiveHolds(seatIds, sessionId)) {
            throw new ApiException(HttpStatus.CONFLICT, SeatHoldService.HOLD_EXPIRED,
                    "Your seat hold expired. Please reselect seats.");
        }
        CheckoutData data = bookingService.buildCheckout(showtimeId, seatIds, resolveEmail(authentication),
                req.promoCode(), req.foodItems());
        return ResponseEntity.ok(toQuoteResponse(data));
    }

    @PostMapping(value = "/promo/validate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Validate a promo code and return its discount percentage")
    public ResponseEntity<Map<String, Object>> validatePromo(@RequestParam("code") String code) {
        Map<String, Object> result = new HashMap<>();
        try {
            int pct = promoCodeService.validate(code);
            result.put("valid", true);
            result.put("discountPercent", pct);
            result.put("message", pct + "% discount applied!");
        } catch (IllegalArgumentException ex) {
            result.put("valid", false);
            result.put("message", ex.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/checkout/{showtimeId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Confirm and pay for the held seats; returns the booking with its tickets")
    public ResponseEntity<BookingDetailResponse> checkout(@PathVariable Long showtimeId,
                           @RequestBody CheckoutRequest req,
                           Authentication authentication,
                           @RequestHeader(value = HoldSessions.HEADER, required = false) String sessionHeader,
                           HttpServletRequest request) {
        String sessionId = HoldSessions.resolve(sessionHeader, request);
        BookingService.Confirmation confirmation = bookingService.confirmCheckout(
                showtimeId, req.seatIds(), resolveEmail(authentication), sessionId,
                req.guestName(), req.guestPhone(), req.guestEmail(),
                req.promoCode(), req.paymentMethod(),
                req.foodItems() != null ? req.foodItems() : Map.of());
        return ResponseEntity.ok(toBookingDetail(confirmation.booking(), confirmation.tickets()));
    }

    @PostMapping(value = "/payway/{showtimeId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Start an ABA KHQR payment for the held seats",
            description = "Same body and X-Session-Id rule as confirm. Writes a PENDING booking and payment, "
                    + "creates the PayWay purchase for the server total, and returns its checkout page. "
                    + "No tickets and no BOOKED seats until PayWay approves. 409 HOLD_EXPIRED when under "
                    + "3 minutes are left on the hold; 402 PAYMENT_FAILED when PayWay refuses the purchase; "
                    + "503 PAYWAY_UNAVAILABLE when the server has no PayWay keys.")
    public ResponseEntity<PaywayStartResponse> startPayway(@PathVariable Long showtimeId,
                                                           @RequestBody CheckoutRequest req,
                                                           Authentication authentication,
                                                           @RequestHeader(value = HoldSessions.HEADER, required = false) String sessionHeader,
                                                           HttpServletRequest request) {
        String sessionId = HoldSessions.resolve(sessionHeader, request);
        PaywayService.Started started = paywayService.start(showtimeId, req.seatIds(), resolveEmail(authentication),
                sessionId, req.guestName(), req.guestPhone(), req.guestEmail(), req.promoCode(),
                req.foodItems() != null ? req.foodItems() : Map.of());
        return ResponseEntity.ok(new PaywayStartResponse(started.bookingId(), started.reference(), started.tranId(),
                started.amount(),
                started.checkoutUrl(), started.abaDeeplink()));
    }

    @GetMapping(value = "/payway/{tranId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Poll an ABA KHQR payment",
            description = "PENDING, APPROVED (with the confirmation), DECLINED, or CANCELLED. "
                    + "Owner only: the same user, or the guest session that started it (403 NOT_OWNER).")
    public ResponseEntity<PaywayStatusResponse> pollPayway(@PathVariable String tranId,
                                                           Authentication authentication,
                                                           @RequestHeader(value = HoldSessions.HEADER, required = false) String sessionHeader,
                                                           HttpServletRequest request) {
        String sessionId = HoldSessions.resolve(sessionHeader, request);
        return ResponseEntity.ok(toPaywayStatus(
                paywayService.poll(tranId, resolveEmail(authentication), sessionId)));
    }

    @PostMapping(value = "/payway/{tranId}/close", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Close a pending ABA KHQR payment (the customer left the checkout page)",
            description = "Closes the PayWay transaction and cancels the booking; seats stay AVAILABLE and the "
                    + "hold is kept. A payment that was approved first is completed and returned as APPROVED.")
    public ResponseEntity<PaywayStatusResponse> closePayway(@PathVariable String tranId,
                                                            Authentication authentication,
                                                            @RequestHeader(value = HoldSessions.HEADER, required = false) String sessionHeader,
                                                            HttpServletRequest request) {
        String sessionId = HoldSessions.resolve(sessionHeader, request);
        return ResponseEntity.ok(toPaywayStatus(
                paywayService.close(tranId, resolveEmail(authentication), sessionId)));
    }

    private PaywayStatusResponse toPaywayStatus(PaywayService.Status status) {
        BookingService.Confirmation c = status.confirmation();
        return new PaywayStatusResponse(status.status(),
                c == null ? null : toBookingDetail(c.booking(), c.tickets()));
    }

    @PostMapping(value = "/cancel/{bookingId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cancel one of the current user's bookings",
            description = "403 NOT_OWNER (not the caller's booking, or a guest booking); "
                    + "409 ALREADY_CANCELLED; 409 CANCEL_WINDOW (inside the cancellation window); "
                    + "400 when the booking does not exist. Freed seats are broadcast as RELEASED.")
    public ResponseEntity<Map<String, Object>> cancelBooking(@PathVariable Long bookingId,
                                Authentication authentication) {
        bookingService.cancelBooking(bookingId, authentication.getName());
        return ResponseEntity.ok(Map.of("success", true, "message", "Booking #" + bookingId + " has been cancelled."));
    }

    @GetMapping(value = "/confirmation/{bookingId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get booking confirmation details and issued tickets",
            description = "Owner only: the signed-in user the booking belongs to, or the X-Session-Id that "
                    + "created it. Anyone else, and a booking that does not exist, get 404 BOOKING_NOT_FOUND.")
    @Transactional(readOnly = true)
    public ResponseEntity<BookingDetailResponse> confirmation(@PathVariable Long bookingId,
                                                              Authentication authentication,
                                                              @RequestHeader(value = HoldSessions.HEADER, required = false) String sessionHeader,
                                                              HttpServletRequest request) {
        String sessionId = HoldSessions.resolve(sessionHeader, request);
        Booking booking = bookingService.findForReader(bookingId, resolveEmail(authentication), sessionId);
        List<Ticket> tickets = ticketService.findByBooking(booking);
        return ResponseEntity.ok(toBookingDetail(booking, tickets));
    }

    @PostMapping(value = "/lookup", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Find a booking by its reference and phone number (guests)",
            description = "Phone numbers are compared on digits only. Any miss is 404 BOOKING_NOT_FOUND. "
                    + "After 5 misses from one client in 10 minutes every lookup is 429 TOO_MANY_ATTEMPTS "
                    + "until the oldest miss is 10 minutes old.")
    @Transactional(readOnly = true)
    public ResponseEntity<BookingDetailResponse> lookup(@RequestBody LookupRequest req, HttpServletRequest request) {
        String client = request.getRemoteAddr();
        if (lookupRateLimiter.isBlocked(client)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_ATTEMPTS,
                    "Too many attempts. Please try again in a few minutes.");
        }
        Booking booking;
        try {
            booking = bookingService.lookup(req.reference(), req.phone());
        } catch (ApiException e) {
            lookupRateLimiter.recordFailure(client);
            throw e;
        }
        return ResponseEntity.ok(toBookingDetail(booking, ticketService.findByBooking(booking)));
    }

    @GetMapping(value = "/my-bookings", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the current user's bookings, newest first")
    @Transactional(readOnly = true)
    public ResponseEntity<List<BookingDetailResponse>> myBookings(Authentication authentication) {
        List<Booking> bookings = bookingService.getBookingsForUser(authentication.getName());
        Map<Long, PaymentStatus> payments = bookingService.paymentStatuses(
                bookings.stream().map(Booking::getId).toList());
        List<BookingDetailResponse> response = bookings.stream()
                .map(booking -> toBookingDetail(booking, ticketService.findByBooking(booking),
                        payments.get(booking.getId())))
                .toList();
        return ResponseEntity.ok(response);
    }

    private String resolveEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return authentication.getName();
    }

    private SeatInfo toSeatInfo(ShowtimeSeat ss) {
        return new SeatInfo(ss.getId(), ss.getSeat().getSeatCode(), ss.getSeat().getSeatType().name(),
                ss.getSeat().getRowNumber(), ss.getStatus().name());
    }

    private ShowtimeInfo toShowtimeInfo(Showtime s) {
        return new ShowtimeInfo(s.getId(), s.getShowDate(), s.getShowTime(), s.getNormalPrice(), s.getCouplePrice(),
                s.isBlocked(), s.getMovie().getTitle(), s.getHall().getId(), s.getHall().getName(), s.getHall().getCity());
    }

    private CheckoutResponse toCheckoutResponse(CheckoutData data, List<Long> seatIds, boolean isAuthenticated) {
        User user = data.getUser();
        UserSummary userSummary = user == null ? null
                : new UserSummary(user.getId(), user.getName(), user.getEmail(), user.getPhone());
        List<SeatInfo> seats = data.getSelectedSeats().stream().map(this::toSeatInfo).toList();
        return new CheckoutResponse(
                userSummary, toShowtimeInfo(data.getShowtime()), seats,
                data.getBaseAmount(), data.getMorningDiscount(), data.getWeekendSurcharge(),
                data.getServiceFee(), data.getTaxAmount(), data.getDiscountAmount(), data.getTotalAmount(),
                data.getPromoCode(), data.getAvailableFoodItems(), seatIds,
                seatHoldService.getHoldMinutes(), isAuthenticated);
    }

    private QuoteResponse toQuoteResponse(CheckoutData data) {
        User user = data.getUser();
        UserSummary userSummary = user == null ? null
                : new UserSummary(user.getId(), user.getName(), user.getEmail(), user.getPhone());
        return new QuoteResponse(
                toShowtimeInfo(data.getShowtime()),
                data.getSelectedSeats().stream().map(this::toSeatInfo).toList(),
                data.getBaseAmount(), data.getMorningDiscount(), data.getWeekendSurcharge(), data.getSubtotal(),
                data.getPromoCode(), data.getDiscountAmount(), data.getTaxAmount(), data.getServiceFee(),
                data.getFoodLines(), data.getFoodTotal(), data.getTotalAmount(),
                data.getAvailableFoodItems(), userSummary);
    }

    private BookingDetailResponse toBookingDetail(Booking booking, List<Ticket> tickets) {
        return toBookingDetail(booking, tickets,
                bookingService.paymentStatuses(List.of(booking.getId())).get(booking.getId()));
    }

    private BookingDetailResponse toBookingDetail(Booking booking, List<Ticket> tickets, PaymentStatus paymentStatus) {
        Showtime showtime = booking.getShowtime();
        User user = booking.getUser();
        List<TicketSummary> ticketSummaries = tickets.stream()
                .map(t -> new TicketSummary(t.getId(), t.getTicketCode(), t.getStatus().name(),
                        t.getSeat().getSeatCode(), t.getSeat().getSeatType().name()))
                .toList();
        return new BookingDetailResponse(
                booking.getId(), booking.getReference(), toShowtimeInfo(showtime),
                user != null ? user.getName() : booking.getGuestName(),
                user != null ? user.getEmail() : booking.getGuestEmail(),
                booking.getGuestPhone(), booking.getTotalAmount(), booking.getServiceFee(),
                booking.getTaxAmount(), booking.getDiscountAmount(), booking.getPromoCode(),
                booking.getPaymentMethod(), booking.getStatus().name(), booking.getBookedAt(),
                ticketSummaries, ticketSummaries.isEmpty() ? null : ticketSummaries.get(0).ticketCode(),
                paymentStatus == null ? null : paymentStatus.name());
    }

    public record SeatInfo(Long id, String seatCode, String seatType, Integer rowNumber, String status) {
    }

    public record ShowtimeInfo(Long id, LocalDate showDate, LocalTime showTime, BigDecimal normalPrice,
                                BigDecimal couplePrice, boolean blocked, String movieTitle,
                                Long hallId, String hallName, String hallCity) {
    }

    public record UserSummary(Long id, String name, String email, String phone) {
    }

    public record TicketSummary(Long id, String ticketCode, String status, String seatCode, String seatType) {
    }

    public record SeatMapResponse(Long showtimeId, ShowtimeInfo showtime, List<SeatInfo> seats,
                                   Set<Long> heldByOthers, Set<Long> heldByMe, String sessionId,
                                   int holdMinutes, boolean isWeekend, boolean isMorning, int maxSeats) {
    }

    public record CheckoutResponse(UserSummary user, ShowtimeInfo showtime, List<SeatInfo> seats,
                                    BigDecimal baseAmount, BigDecimal morningDiscount, BigDecimal weekendSurcharge,
                                    BigDecimal serviceFee, BigDecimal taxAmount, BigDecimal discountAmount,
                                    BigDecimal totalAmount, String promoCode, List<FoodItem> availableFoodItems,
                                    List<Long> seatIds, int holdMinutes, boolean isAuthenticated) {
    }

    public record QuoteRequest(List<Long> seatIds, String promoCode, Map<Long, Integer> foodItems) {
    }

    /** Every price line of analytics 3.4, in display order. All amounts are computed by the server. */
    public record QuoteResponse(ShowtimeInfo showtime, List<SeatInfo> seats,
                                BigDecimal baseAmount, BigDecimal morningDiscount, BigDecimal weekendSurcharge,
                                BigDecimal subtotal, String promoCode, BigDecimal discountAmount,
                                BigDecimal taxAmount, BigDecimal serviceFee,
                                List<CheckoutData.FoodLine> foodLines, BigDecimal foodTotal, BigDecimal totalAmount,
                                List<FoodItem> availableFoodItems, UserSummary user) {
    }

    public record CheckoutRequest(List<Long> seatIds, String guestName, String guestPhone, String guestEmail,
                                   String promoCode, String paymentMethod, Map<Long, Integer> foodItems) {
    }

    /** {@code checkoutUrl} is PayWay's {@code checkout_qr_url}; {@code abaDeeplink} its {@code abapay_deeplink}. */
    public record PaywayStartResponse(Long bookingId, String reference, String tranId, BigDecimal amount, String checkoutUrl,
                                      String abaDeeplink) {
    }

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record PaywayStatusResponse(String status, BookingDetailResponse confirmation) {
    }

    public record LookupRequest(String reference, String phone) {
    }

    public record BookingDetailResponse(Long id, String reference, ShowtimeInfo showtime, String customerName, String customerEmail,
                                         String customerPhone, BigDecimal totalAmount, BigDecimal serviceFee,
                                         BigDecimal taxAmount, BigDecimal discountAmount, String promoCode,
                                         String paymentMethod, String status, LocalDateTime bookedAt,
                                         List<TicketSummary> tickets, String firstTicketCode,
                                         String paymentStatus) {
    }
}
