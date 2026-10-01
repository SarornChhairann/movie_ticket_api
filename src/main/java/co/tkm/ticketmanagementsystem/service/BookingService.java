package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import org.springframework.http.HttpStatus;

import co.tkm.ticketmanagementsystem.dto.CheckoutData;
import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.enums.TicketStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.BookingFoodItem;
import co.tkm.ticketmanagementsystem.model.FoodItem;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.BookingFoodItemRepository;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import co.tkm.ticketmanagementsystem.repository.TicketRepository;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookingService {

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
        // 1-2. Lock, recheck availability and couple pairs, and recompute the price.
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

        // 3-4. Booking PENDING plus food lines.
        Booking booking = new Booking();
        booking.setUser(user);
        if (user == null) {
            booking.setGuestName(guestName.trim());
            booking.setGuestPhone(guestPhone.trim());
            if (!isBlank(guestEmail)) {
                booking.setGuestEmail(guestEmail.trim());
            }
        }
        String method = isBlank(paymentMethod) ? "CARD" : paymentMethod.trim();
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
        savedBooking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(savedBooking);
        List<Ticket> tickets = ticketService.issueTickets(savedBooking, checkoutData.getSelectedSeats());
        if (checkoutData.getPromoCode() != null) {
            promoCodeService.recordUsage(checkoutData.getPromoCode());
        }
        if (user != null) {
            user.setPoints(user.getPoints() + checkoutData.getTotalAmount().intValue());
            userRepository.save(user);
        }
        seatHoldService.releaseHolds(sessionId);

        List<Long> bookedSeatIds = List.copyOf(selectedSeatIds);
        afterCommit(() -> {
            seatRealtimeService.publishBooked(showtimeId, bookedSeatIds);
            emailService.sendBookingConfirmation(savedBooking, tickets);
        });
        return new Confirmation(savedBooking, tickets);
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
