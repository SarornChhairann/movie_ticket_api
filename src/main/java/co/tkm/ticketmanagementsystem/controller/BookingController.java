package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.dto.CheckoutData;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.FoodItem;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.service.BookingService;
import co.tkm.ticketmanagementsystem.service.PromoCodeService;
import co.tkm.ticketmanagementsystem.service.SeatHoldService;
import co.tkm.ticketmanagementsystem.service.SeatRealtimeService;
import co.tkm.ticketmanagementsystem.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
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

    @GetMapping(value = "/seats/{showtimeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the seat map for a showtime, including which seats are held")
    @Transactional(readOnly = true)
    public ResponseEntity<SeatMapResponse> seatMap(@PathVariable Long showtimeId, HttpSession session) {
        List<ShowtimeSeat> showtimeSeats = bookingService.getSeatsForShowtime(showtimeId);
        String sessionId = session.getId();
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
                sessionId, seatHoldService.getHoldMinutes(), isWeekend, isMorning));
    }

    @GetMapping(value = "/checkout/{showtimeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Preview checkout totals for the selected seats and (optionally) a promo code; places a temporary hold on the seats")
    public ResponseEntity<CheckoutResponse> checkoutPreview(@PathVariable Long showtimeId,
                                  @RequestParam("seatIds") List<Long> seatIds,
                                  @RequestParam(value = "promoCode", required = false) String promoCode,
                                  Authentication authentication,
                                  HttpSession session) {
        String userEmail = resolveEmail(authentication);
        if (seatHoldService.hasActiveConflict(seatIds, session.getId())) {
            throw new IllegalStateException("Some seats are currently held by another user. Please reselect.");
        }
        CheckoutData checkoutData = bookingService.buildCheckout(showtimeId, seatIds, userEmail, promoCode);
        seatHoldService.holdSeatsRealtime(showtimeId, seatIds, session.getId());
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());

        return ResponseEntity.ok(toCheckoutResponse(checkoutData, seatIds, isAuthenticated));
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
    @Operation(summary = "Confirm a booking for the held seats")
    public ResponseEntity<Map<String, Object>> checkout(@PathVariable Long showtimeId,
                           @RequestBody CheckoutRequest req,
                           Authentication authentication,
                           HttpSession session) {
        String userEmail = resolveEmail(authentication);
        List<Long> seatIds = req.seatIds();
        Map<Long, Integer> foodSelections = req.foodItems() != null ? req.foodItems() : Map.of();

        if (seatHoldService.hasActiveConflict(seatIds, session.getId())) {
            throw new IllegalStateException("Some seats were selected by another user. Please reselect.");
        }
        if (!seatHoldService.ownsActiveHolds(seatIds, session.getId())) {
            throw new IllegalStateException("Your seat hold expired. Please reselect seats.");
        }
        Booking booking = bookingService.confirmCheckout(
                showtimeId, seatIds, userEmail,
                req.guestName(), req.guestPhone(), req.guestEmail(),
                req.promoCode(), req.paymentMethod(), foodSelections);
        seatHoldService.releaseHolds(session.getId());
        seatRealtimeService.publishBooked(showtimeId, seatIds);

        return ResponseEntity.ok(Map.of("bookingId", booking.getId()));
    }

    @PostMapping(value = "/cancel/{bookingId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cancel one of the current user's bookings")
    public ResponseEntity<Map<String, Object>> cancelBooking(@PathVariable Long bookingId,
                                Authentication authentication) {
        bookingService.cancelBooking(bookingId, authentication.getName());
        return ResponseEntity.ok(Map.of("success", true, "message", "Booking #" + bookingId + " has been cancelled."));
    }

    @GetMapping(value = "/confirmation/{bookingId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get booking confirmation details and issued tickets")
    @Transactional(readOnly = true)
    public ResponseEntity<BookingDetailResponse> confirmation(@PathVariable Long bookingId) {
        Booking booking = bookingService.findById(bookingId);
        List<Ticket> tickets = ticketService.findByBooking(booking);
        return ResponseEntity.ok(toBookingDetail(booking, tickets));
    }

    @GetMapping(value = "/my-bookings", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the current user's bookings")
    @Transactional(readOnly = true)
    public ResponseEntity<List<BookingDetailResponse>> myBookings(Authentication authentication) {
        List<Booking> bookings = bookingService.getBookingsForUser(authentication.getName());
        List<BookingDetailResponse> response = bookings.stream()
                .map(booking -> toBookingDetail(booking, ticketService.findByBooking(booking)))
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

    private BookingDetailResponse toBookingDetail(Booking booking, List<Ticket> tickets) {
        Showtime showtime = booking.getShowtime();
        User user = booking.getUser();
        List<TicketSummary> ticketSummaries = tickets.stream()
                .map(t -> new TicketSummary(t.getId(), t.getTicketCode(), t.getStatus().name(),
                        t.getSeat().getSeatCode(), t.getSeat().getSeatType().name()))
                .toList();
        return new BookingDetailResponse(
                booking.getId(), toShowtimeInfo(showtime),
                user != null ? user.getName() : booking.getGuestName(),
                user != null ? user.getEmail() : booking.getGuestEmail(),
                booking.getGuestPhone(), booking.getTotalAmount(), booking.getServiceFee(),
                booking.getTaxAmount(), booking.getDiscountAmount(), booking.getPromoCode(),
                booking.getPaymentMethod(), booking.getStatus().name(), booking.getBookedAt(),
                ticketSummaries, ticketSummaries.isEmpty() ? null : ticketSummaries.get(0).ticketCode());
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
                                   int holdMinutes, boolean isWeekend, boolean isMorning) {
    }

    public record CheckoutResponse(UserSummary user, ShowtimeInfo showtime, List<SeatInfo> seats,
                                    BigDecimal baseAmount, BigDecimal morningDiscount, BigDecimal weekendSurcharge,
                                    BigDecimal serviceFee, BigDecimal taxAmount, BigDecimal discountAmount,
                                    BigDecimal totalAmount, String promoCode, List<FoodItem> availableFoodItems,
                                    List<Long> seatIds, int holdMinutes, boolean isAuthenticated) {
    }

    public record CheckoutRequest(List<Long> seatIds, String guestName, String guestPhone, String guestEmail,
                                   String promoCode, String paymentMethod, Map<Long, Integer> foodItems) {
    }

    public record BookingDetailResponse(Long id, ShowtimeInfo showtime, String customerName, String customerEmail,
                                         String customerPhone, BigDecimal totalAmount, BigDecimal serviceFee,
                                         BigDecimal taxAmount, BigDecimal discountAmount, String promoCode,
                                         String paymentMethod, String status, LocalDateTime bookedAt,
                                         List<TicketSummary> tickets, String firstTicketCode) {
    }
}
