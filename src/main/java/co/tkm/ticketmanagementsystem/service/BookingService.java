package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import org.springframework.http.HttpStatus;

import co.tkm.ticketmanagementsystem.dto.CheckoutData;
import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.enums.PaymentStatus;
import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.enums.TicketStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.BookingFoodItem;
import co.tkm.ticketmanagementsystem.model.FoodItem;
import co.tkm.ticketmanagementsystem.model.Payment;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.BookingFoodItemRepository;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.PaymentRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import co.tkm.ticketmanagementsystem.repository.TicketRepository;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    public static final String PROMO_INVALID = "PROMO_INVALID";
    public static final String NOT_OWNER = "NOT_OWNER";
    public static final String ALREADY_CANCELLED = "ALREADY_CANCELLED";
    public static final String CANCEL_WINDOW = "CANCEL_WINDOW";

    // Fallback defaults (overridden by SystemSettingService at runtime)
    private static final BigDecimal DEFAULT_SERVICE_FEE = new BigDecimal("1.50");
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.10");
    private static final BigDecimal DEFAULT_WEEKEND_SURCHARGE_RATE = new BigDecimal("0.10");
    private static final BigDecimal DEFAULT_MORNING_DISCOUNT_RATE = new BigDecimal("0.10");
    private static final int DEFAULT_CANCELLATION_WINDOW_HOURS = 2;

    private final BookingRepository bookingRepository;
    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final BookingFoodItemRepository bookingFoodItemRepository;
    private final PaymentService paymentService;
    private final TicketService ticketService;
    private final EmailService emailService;
    private final PromoCodeService promoCodeService;
    private final FoodItemService foodItemService;
    private final SystemSettingService systemSettingService;
    private final PaymentGateway paymentGateway;
    private final SeatHoldService seatHoldService;
    private final SeatRealtimeService seatRealtimeService;
    private final PaymentRepository paymentRepository;

    public List<ShowtimeSeat> getSeatsForShowtime(Long showtimeId) {
        Showtime showtime = findShowtime(showtimeId);
        return showtimeSeatRepository.findByShowtimeOrderBySeatRowNumberAscSeatIdAsc(showtime);
    }

    @Transactional(readOnly = true)
    public CheckoutData buildCheckout(Long showtimeId, List<Long> selectedSeatIds, String userEmail,
                                      String promoCode) {
        return buildCheckout(showtimeId, selectedSeatIds, userEmail, promoCode, Collections.emptyMap());
    }

    /** The quote: same math as confirm, but no lock and no writes. */
    @Transactional(readOnly = true)
    public CheckoutData buildCheckout(Long showtimeId, List<Long> selectedSeatIds, String userEmail,
                                      String promoCode, Map<Long, Integer> foodSelections) {
        return resolveCheckoutData(showtimeId, selectedSeatIds, userEmail, false, promoCode,
                foodSelections == null ? Collections.emptyMap() : foodSelections);
    }

    /**
     * The confirm transaction (analytics 3.5 / 6.3). Seats are locked first, so two
     * confirms for the same seat serialize and the loser sees {@code SEAT_BOOKED}.
     * Tickets, booked seats, promo usage, and points happen only after the charge is
     * {@code PAID}; a declined charge leaves a cancelled booking and nothing else.
     */
    @Transactional(noRollbackFor = PaymentFailedException.class)
    public Confirmation confirmCheckout(Long showtimeId, List<Long> selectedSeatIds, String userEmail,
                                        String sessionId, String guestName, String guestPhone, String guestEmail,
                                        String promoCode, String paymentMethod,
                                        Map<Long, Integer> foodSelections) {
        // 1-2. Lock, recheck availability, couple pairs, hold, and account; recompute the price.
        CheckoutData checkoutData = lockAndValidate(showtimeId, selectedSeatIds, userEmail, sessionId,
                guestName, guestPhone, promoCode, foodSelections);

        // 3-4. Booking PENDING plus food lines.
        String method = isBlank(paymentMethod) ? "CARD" : paymentMethod.trim();
        Booking savedBooking = savePendingBooking(checkoutData, method, guestName, guestPhone, guestEmail,
                foodSelections);

        // 5. Charge.
        PaymentGateway.PaymentResult result = paymentGateway.charge(savedBooking, method);
        paymentService.record(savedBooking, method, result);

        if (!result.paid()) {
            // Seats stay AVAILABLE, no tickets, no promo use, no points; the hold is kept for a retry.
            savedBooking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(savedBooking);
            throw new PaymentFailedException(result.reason());
        }

        // 6. PAID: confirm, issue tickets (marks seats BOOKED), then the side effects.
        List<Ticket> tickets = finishPaid(savedBooking, checkoutData.getSelectedSeats(), selectedSeatIds, sessionId);
        return new Confirmation(savedBooking, tickets);
    }

    /** Steps 1-2 of confirm: lock the seats, recompute the quote, require the hold and a usable account. */
    private CheckoutData lockAndValidate(Long showtimeId, List<Long> selectedSeatIds, String userEmail,
                                         String sessionId, String guestName, String guestPhone,
                                         String promoCode, Map<Long, Integer> foodSelections) {
        CheckoutData checkoutData = resolveCheckoutData(showtimeId, selectedSeatIds, userEmail, true,
                promoCode, foodSelections);

        // Hold ownership, checked under the lock.
        if (seatHoldService.hasActiveConflict(selectedSeatIds, sessionId)) {
            throw new ApiException(HttpStatus.CONFLICT, SeatHoldService.SEAT_HELD,
                    "Some seats were selected by another user. Please reselect.");
        }
        if (!seatHoldService.ownsActiveHolds(selectedSeatIds, sessionId)) {
            throw new ApiException(HttpStatus.CONFLICT, SeatHoldService.HOLD_EXPIRED,
                    "Your seat hold expired. Please reselect seats.");
        }

        User user = checkoutData.getUser();
        if (user != null && user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, AuthService.ACCOUNT_BLOCKED,
                    "This account is " + user.getStatus().name().toLowerCase() + ".");
        }
        if (user == null && (isBlank(guestName) || isBlank(guestPhone))) {
            throw new IllegalArgumentException("Please enter your name and phone number.");
        }
        return checkoutData;
    }

    /** Steps 3-4 of confirm: the {@code PENDING} booking and its food lines. */
    private Booking savePendingBooking(CheckoutData checkoutData, String method, String guestName,
                                       String guestPhone, String guestEmail, Map<Long, Integer> foodSelections) {
        User user = checkoutData.getUser();
        Booking booking = new Booking();
        booking.setUser(user);
        if (user == null) {
            booking.setGuestName(guestName.trim());
            booking.setGuestPhone(guestPhone.trim());
            if (!isBlank(guestEmail)) {
                booking.setGuestEmail(guestEmail.trim());
            }
        }
        booking.setShowtime(checkoutData.getShowtime());
        booking.setTotalAmount(checkoutData.getTotalAmount());
        booking.setServiceFee(checkoutData.getServiceFee());
        booking.setTaxAmount(checkoutData.getTaxAmount());
        booking.setDiscountAmount(checkoutData.getDiscountAmount());
        booking.setPromoCode(checkoutData.getPromoCode());
        booking.setPaymentMethod(method);
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookedAt(LocalDateTime.now());
        Booking savedBooking = bookingRepository.save(booking);
        saveFoodItems(savedBooking, foodSelections);
        return savedBooking;
    }

    /**
     * The paid path, shared by the in-app gateway and PayWay: {@code CONFIRMED}, tickets
     * (seats {@code BOOKED}), promo use, points, holds released, then the {@code BOOKED}
     * broadcast and the email once the transaction commits.
     */
    private List<Ticket> finishPaid(Booking booking, List<ShowtimeSeat> seats, List<Long> seatIds, String sessionId) {
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);
        List<Ticket> tickets = ticketService.issueTickets(booking, seats);
        if (booking.getPromoCode() != null) {
            promoCodeService.recordUsage(booking.getPromoCode());
        }
        User user = booking.getUser();
        if (user != null) {
            user.setPoints(user.getPoints() + booking.getTotalAmount().intValue());
            userRepository.save(user);
        }
        seatHoldService.releaseHolds(sessionId);

        Long showtimeId = booking.getShowtime().getId();
        List<Long> bookedSeatIds = List.copyOf(seatIds);
        afterCommit(() -> {
            seatRealtimeService.publishBooked(showtimeId, bookedSeatIds);
            emailService.sendBookingConfirmation(booking, tickets);
        });
        return tickets;
    }

    // ------------------------------------------------------------------ ABA PayWay

    /** PayWay's minimum payment lifetime, in minutes. */
    public static final int PAYWAY_MIN_LIFETIME_MINUTES = 3;
    public static final String PAYMENT_CLOSED = "PAYMENT_CLOSED";
    public static final String ABA_KHQR = "ABA_KHQR";

    /**
     * Start of a PayWay payment: confirm steps 1-4 under the seat lock, then a
     * {@code PENDING} payment carrying a fresh {@code tran_id}. No charge, no tickets, no
     * {@code BOOKED} seats, and the hold stays. Fails with {@code HOLD_EXPIRED} when fewer
     * than {@link #PAYWAY_MIN_LIFETIME_MINUTES} minutes are left, before anything is written.
     * Earlier pending PayWay payments of this session are cancelled; their ids come back
     * so the caller can close them at PayWay.
     */
    @Transactional
    public PaywayBooking openPaywayBooking(Long showtimeId, List<Long> selectedSeatIds, String userEmail,
                                           String sessionId, String guestName, String guestPhone,
                                           String guestEmail, String promoCode,
                                           Map<Long, Integer> foodSelections) {
        CheckoutData checkoutData = lockAndValidate(showtimeId, selectedSeatIds, userEmail, sessionId,
                guestName, guestPhone, promoCode, foodSelections);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime holdEnds = seatHoldService.earliestExpiry(selectedSeatIds, sessionId).orElse(now);
        long lifetime = Duration.between(now, holdEnds).toMinutes();
        if (lifetime < PAYWAY_MIN_LIFETIME_MINUTES) {
            throw new ApiException(HttpStatus.CONFLICT, SeatHoldService.HOLD_EXPIRED,
                    "Less than " + PAYWAY_MIN_LIFETIME_MINUTES + " minutes are left on your seat hold. Please reselect seats.");
        }

        List<String> superseded = new ArrayList<>();
        for (String staleTranId : paymentRepository.findTranIdsBySessionAndStatus(sessionId, PaymentStatus.PENDING)) {
            if (cancelPayway(staleTranId, "SUPERSEDED")) {
                superseded.add(staleTranId);
            }
        }

        Booking booking = savePendingBooking(checkoutData, ABA_KHQR, guestName, guestPhone, guestEmail,
                foodSelections);
        String tranId = newTranId(booking.getId());
        String seatIds = selectedSeatIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        paymentService.recordPending(booking, ABA_KHQR, tranId, sessionId, seatIds, holdEnds);
        return new PaywayBooking(booking, checkoutData, tranId, (int) lifetime, superseded);
    }

    /** A pending PayWay booking, what Purchase needs to describe it, and the payments it superseded. */
    public record PaywayBooking(Booking booking, CheckoutData checkoutData, String tranId,
                                int lifetimeMinutes, List<String> supersededTranIds) {
    }

    /** Where a PayWay payment stands locally, and who may look at it. */
    public record PaywayState(BookingStatus bookingStatus, String providerStatus, String ownerEmail,
                              String sessionId) {
    }

    @Transactional(readOnly = true)
    public PaywayState paywayState(String tranId) {
        Payment payment = paymentRepository.findByTranId(tranId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + tranId));
        Booking booking = payment.getBooking();
        String ownerEmail = booking.getUser() != null ? booking.getUser().getEmail() : null;
        return new PaywayState(booking.getStatus(), payment.getProviderStatus(), ownerEmail, payment.getSessionId());
    }

    /**
     * PayWay approved the payment: run the paid path exactly once. The payment row is
     * locked, so a poll and the callback racing each other serialize; the loser sees
     * {@code CONFIRMED} and gets the same confirmation back without a second ticket or
     * a second round of points. A payment closed or declined earlier never confirms.
     */
    @Transactional
    public Confirmation completePayway(String tranId) {
        Payment payment = paymentRepository.findByTranIdForUpdate(tranId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + tranId));
        Booking booking = payment.getBooking();
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return loaded(new Confirmation(booking, ticketRepository.findByBooking(booking)));
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            log.warn("PayWay approved {} after it was {}; booking {} stays cancelled",
                    tranId, payment.getProviderStatus(), booking.getId());
            throw new ApiException(HttpStatus.CONFLICT, PAYMENT_CLOSED, "This payment was already closed.");
        }

        List<Long> seatIds = parseSeatIds(payment.getSeatIds());
        List<ShowtimeSeat> seats = showtimeSeatRepository
                .findAllByShowtimeIdAndIdsForUpdate(booking.getShowtime().getId(), seatIds);
        for (ShowtimeSeat seat : seats) {
            if (seat.getStatus() != ShowtimeSeatStatus.AVAILABLE) {
                log.error("PayWay approved {} but seat {} is no longer available", tranId, seat.getId());
                throw new ApiException(HttpStatus.CONFLICT, SeatHoldService.SEAT_BOOKED,
                        "Some seats were already booked. Please contact the cinema.");
            }
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setProviderStatus("APPROVED");
        paymentRepository.save(payment);
        List<Ticket> tickets = finishPaid(booking, seats, seatIds, payment.getSessionId());
        return loaded(new Confirmation(booking, tickets));
    }

    /**
     * A PayWay payment that will not be paid (declined, cancelled, closed, expired,
     * superseded, or Purchase failed): booking {@code CANCELLED}, payment {@code FAILED},
     * seats untouched, hold kept. Returns false when it was no longer pending.
     */
    @Transactional
    public boolean cancelPayway(String tranId, String providerStatus) {
        Payment payment = paymentRepository.findByTranIdForUpdate(tranId).orElse(null);
        if (payment == null || payment.getBooking().getStatus() != BookingStatus.PENDING) {
            return false;
        }
        Booking booking = payment.getBooking();
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setProviderStatus(providerStatus);
        paymentRepository.save(payment);
        return true;
    }

    /** Stores a non-approving status PayWay reported (e.g. a callback) without changing the booking. */
    @Transactional
    public void recordPaywayStatus(String tranId, String providerStatus) {
        paymentRepository.findByTranIdForUpdate(tranId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setProviderStatus(providerStatus);
                paymentRepository.save(payment);
            }
        });
    }

    /** Touches every lazy relation the confirmation response walks, inside the transaction. */
    private static Confirmation loaded(Confirmation confirmation) {
        Booking booking = confirmation.booking();
        booking.getShowtime().getMovie().getTitle();
        booking.getShowtime().getHall().getName();
        if (booking.getUser() != null) {
            booking.getUser().getName();
        }
        confirmation.tickets().forEach(t -> t.getSeat().getSeatCode());
        return confirmation;
    }

    private static List<Long> parseSeatIds(String seatIds) {
        if (seatIds == null || seatIds.isBlank()) return List.of();
        return Arrays.stream(seatIds.split(",")).map(String::trim).map(Long::valueOf).toList();
    }

    /** Unique and at most 20 characters: booking id and the millisecond clock, both base 36. */
    static String newTranId(Long bookingId) {
        return ("CK" + Long.toString(bookingId, 36) + "T" + Long.toString(System.currentTimeMillis(), 36))
                .toUpperCase();
    }

    /** A confirmed booking and the tickets it issued. */
    public record Confirmation(Booking booking, List<Ticket> tickets) {
    }

    /** Runs [action] once the surrounding transaction commits (immediately when there is none). */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void saveFoodItems(Booking booking, Map<Long, Integer> foodSelections) {
        if (foodSelections == null || foodSelections.isEmpty()) return;
        for (Map.Entry<Long, Integer> entry : foodSelections.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) continue;
            FoodItem item = foodItemService.findById(entry.getKey());
            BookingFoodItem bfi = new BookingFoodItem();
            bfi.setBooking(booking);
            bfi.setFoodItem(item);
            bfi.setQuantity(entry.getValue());
            bfi.setUnitPrice(item.getPrice());
            bookingFoodItemRepository.save(bfi);
        }
    }

    /**
     * Customer cancel (analytics 3.6, invariant 7): owner only, never a guest booking, and
     * not inside the cancellation window. Frees the seats and, once committed, tells open
     * seat maps with {@code RELEASED}. Points and promo usage are not reversed.
     */
    @Transactional
    public void cancelBooking(Long bookingId, String userEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getUser() == null || !booking.getUser().getEmail().equals(userEmail)) {
            throw new ApiException(HttpStatus.FORBIDDEN, NOT_OWNER,
                    "You are not authorized to cancel this booking.");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_CANCELLED, "Booking is already cancelled.");
        }

        // Enforce cancellation window from system settings
        int cancellationHours = systemSettingService.getInt(
                SystemSettingService.CANCELLATION_HOURS, DEFAULT_CANCELLATION_WINDOW_HOURS);
        LocalDateTime showtimeStart = LocalDateTime.of(
                booking.getShowtime().getShowDate(),
                booking.getShowtime().getShowTime() != null ? booking.getShowtime().getShowTime() : LocalTime.MIDNIGHT
        );
        if (LocalDateTime.now().isAfter(showtimeStart.minusHours(cancellationHours))) {
            throw new ApiException(HttpStatus.CONFLICT, CANCEL_WINDOW,
                    "Cancellations are only allowed more than " + cancellationHours +
                    " hours before the showtime.");
        }

        List<Long> freedSeatIds = cancelTicketsAndFreeSeats(booking);
        Long showtimeId = booking.getShowtime().getId();
        afterCommit(() -> {
            if (!freedSeatIds.isEmpty()) {
                seatRealtimeService.publishReleased(showtimeId, freedSeatIds, null);
            }
            emailService.sendCancellationNotice(booking);
        });
    }

    /** Sets every ticket and the booking {@code CANCELLED}; returns the freed showtime seat ids. */
    private List<Long> cancelTicketsAndFreeSeats(Booking booking) {
        List<Long> freedSeatIds = new ArrayList<>();
        List<Ticket> tickets = ticketRepository.findByBooking(booking);
        for (Ticket ticket : tickets) {
            ticket.setStatus(TicketStatus.CANCELLED);
            showtimeSeatRepository
                    .findByShowtimeAndSeat(booking.getShowtime(), ticket.getSeat())
                    .ifPresent(ss -> {
                        ss.setStatus(ShowtimeSeatStatus.AVAILABLE);
                        showtimeSeatRepository.save(ss);
                        freedSeatIds.add(ss.getId());
                    });
        }
        ticketRepository.saveAll(tickets);
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        return freedSeatIds;
    }

    @Transactional
    public void adminCancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalArgumentException("Booking is already cancelled.");
        }
        cancelTicketsAndFreeSeats(booking);
        emailService.sendCancellationNotice(booking);
    }

    @Transactional(readOnly = true)
    public Booking findById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + id));
        booking.getShowtime().getMovie().getTitle();
        return booking;
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingsForUser(String userEmail) {
        User user = findUser(userEmail);
        List<Booking> bookings = bookingRepository.findByUserOrderByBookedAtDesc(user);
        bookings.forEach(booking -> {
            booking.getShowtime().getShowDate();
            booking.getShowtime().getShowTime();
            booking.getShowtime().getMovie().getTitle();
        });
        return bookings;
    }

    @Transactional(readOnly = true)
    public List<Booking> getAllBookingsForAdmin() {
        List<Booking> bookings = bookingRepository.findAll();
        
        bookings.forEach(booking -> {
            booking.getShowtime().getShowDate();
            booking.getShowtime().getShowTime();
            booking.getShowtime().getMovie().getTitle();
            if (booking.getUser() != null) {
                booking.getUser().getName();
            }
        });
        return bookings;
    }

    private CheckoutData resolveCheckoutData(Long showtimeId, List<Long> selectedSeatIds,
                                              String userEmail, boolean lockSeats,
                                              String promoCode, Map<Long, Integer> foodSelections) {
        if (selectedSeatIds == null || selectedSeatIds.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one seat.");
        }

        User user = (userEmail != null) ? userRepository.findByEmail(userEmail).orElse(null) : null;
        Showtime showtime = findShowtime(showtimeId);

        List<ShowtimeSeat> selectedSeats = lockSeats
                ? showtimeSeatRepository.findAllByShowtimeIdAndIdsForUpdate(showtimeId, selectedSeatIds)
                : showtimeSeatRepository.findAllById(selectedSeatIds);

        if (selectedSeats.size() != selectedSeatIds.size()) {
            throw new IllegalArgumentException("Some selected seats are invalid.");
        }

        for (ShowtimeSeat seat : selectedSeats) {
            if (!seat.getShowtime().getId().equals(showtimeId)) {
                throw new IllegalArgumentException("Selected seats must belong to the chosen showtime.");
            }
            if (seat.getStatus() != ShowtimeSeatStatus.AVAILABLE) {
                throw new ApiException(HttpStatus.CONFLICT, SeatHoldService.SEAT_BOOKED,
                        "Some seats were already booked. Please reselect seats.");
            }
        }

        int coupleSeatCount = CouplePairs.validate(selectedSeats);
        int normalSeatCount = selectedSeats.size() - coupleSeatCount;

        // Base seat price
        BigDecimal base = BigDecimal.ZERO;
        if (normalSeatCount > 0) {
            base = base.add(showtime.getNormalPrice().multiply(BigDecimal.valueOf(normalSeatCount)));
        }
        if (coupleSeatCount > 0) {
            base = base.add(showtime.getCouplePrice().multiply(BigDecimal.valueOf(coupleSeatCount / 2)));
        }

        // Load configurable rates
        BigDecimal weekendSurchargeRate = systemSettingService.getDecimal(
                SystemSettingService.WEEKEND_SURCHARGE_RATE, DEFAULT_WEEKEND_SURCHARGE_RATE);
        BigDecimal morningDiscountRate = systemSettingService.getDecimal(
                SystemSettingService.MORNING_DISCOUNT_RATE, DEFAULT_MORNING_DISCOUNT_RATE);
        BigDecimal taxRate = systemSettingService.getDecimal(
                SystemSettingService.TAX_RATE, DEFAULT_TAX_RATE);
        BigDecimal serviceFeeAmt = systemSettingService.getDecimal(
                SystemSettingService.SERVICE_FEE, DEFAULT_SERVICE_FEE);

        // Morning discount (showtimes before noon = cheaper)
        boolean isMorning = showtime.getShowTime() != null
                && showtime.getShowTime().isBefore(LocalTime.NOON);
        BigDecimal morningDiscount = isMorning
                ? base.multiply(morningDiscountRate).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal effectiveBase = base.subtract(morningDiscount);

        // Weekend surcharge (applied to effective base after morning discount)
        DayOfWeek dow = showtime.getShowDate().getDayOfWeek();
        boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        BigDecimal weekendSurcharge = isWeekend
                ? effectiveBase.multiply(weekendSurchargeRate).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal subtotal = effectiveBase.add(weekendSurcharge);

        // Tax
        BigDecimal taxAmount = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);

        // Service fee
        BigDecimal serviceFee = serviceFeeAmt;

        // Food cost
        BigDecimal foodTotal = BigDecimal.ZERO;
        List<CheckoutData.FoodLine> foodLines = new ArrayList<>();
        if (foodSelections != null && !foodSelections.isEmpty()) {
            for (Map.Entry<Long, Integer> entry : foodSelections.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) continue;
                FoodItem item = foodItemService.findById(entry.getKey());
                if (!item.isAvailable()) {
                    throw new IllegalArgumentException(item.getName() + " is not available.");
                }
                BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(entry.getValue()));
                foodLines.add(new CheckoutData.FoodLine(item.getId(), item.getName(), item.getPrice(),
                        entry.getValue(), lineTotal));
                foodTotal = foodTotal.add(lineTotal);
            }
        }

        // Promo discount (applied to subtotal before tax, not on fees or food)
        BigDecimal discountAmount = BigDecimal.ZERO;
        String appliedPromo = null;
        if (promoCode != null && !promoCode.isBlank()) {
            int discountPct;
            try {
                discountPct = promoCodeService.validateWithSubtotal(promoCode, subtotal);
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, PROMO_INVALID, e.getMessage());
            }
            discountAmount = subtotal.multiply(BigDecimal.valueOf(discountPct))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            appliedPromo = promoCode.trim().toUpperCase();
        }

        BigDecimal total = subtotal.subtract(discountAmount).add(taxAmount).add(serviceFee).add(foodTotal);
        total = total.setScale(2, RoundingMode.HALF_UP);

        List<FoodItem> availableFoodItems = foodItemService.findAvailable();

        showtime.getMovie().getTitle();
        showtime.getHall().getName();
        selectedSeats.forEach(seat -> seat.getSeat().getSeatCode());

        return new CheckoutData(user, showtime, selectedSeats,
                base, morningDiscount, weekendSurcharge, subtotal, serviceFee, taxAmount,
                discountAmount, total, foodTotal, foodLines, appliedPromo, availableFoodItems, foodSelections);
    }

    @Transactional(readOnly = true)
    public long countBookingsForUser(String email) {
        return userRepository.findByEmail(email)
                .map(bookingRepository::countByUser)
                .orElse(0L);
    }

    @Transactional(readOnly = true)
    public List<Booking> searchBookings(String query) {
        List<Booking> bookings = bookingRepository.searchBookings(query == null ? "" : query);
        bookings.forEach(booking -> {
            booking.getShowtime().getShowDate();
            booking.getShowtime().getMovie().getTitle();
            if (booking.getUser() != null) booking.getUser().getName();
        });
        return bookings;
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingsByUser(co.tkm.ticketmanagementsystem.model.User user) {
        List<Booking> bookings = bookingRepository.findByUserOrderByBookedAtDesc(user);
        bookings.forEach(b -> b.getShowtime().getMovie().getTitle());
        return bookings;
    }

    @Transactional(readOnly = true)
    public Showtime getShowtime(Long id) {
        Showtime st = findShowtime(id);
        st.getMovie().getTitle();
        st.getHall().getName();
        st.getHall().getCity();
        return st;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private Showtime findShowtime(Long showtimeId) {
        return showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found: " + showtimeId));
    }
}
