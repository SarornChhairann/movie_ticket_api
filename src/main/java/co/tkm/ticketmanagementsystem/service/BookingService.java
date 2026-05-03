package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.dto.CheckoutData;
import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BookingService {

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

    public List<ShowtimeSeat> getSeatsForShowtime(Long showtimeId) {
        Showtime showtime = findShowtime(showtimeId);
        return showtimeSeatRepository.findByShowtimeOrderBySeatRowNumberAscSeatIdAsc(showtime);
    }

    @Transactional(readOnly = true)
    public CheckoutData buildCheckout(Long showtimeId, List<Long> selectedSeatIds, String userEmail,
                                      String promoCode) {
        return resolveCheckoutData(showtimeId, selectedSeatIds, userEmail, false, promoCode,
                Collections.emptyMap());
    }

    @Transactional
    public Booking confirmCheckout(Long showtimeId, List<Long> selectedSeatIds, String userEmail,
                                   String guestName, String guestPhone, String guestEmail,
                                   String promoCode, String paymentMethod,
                                   Map<Long, Integer> foodSelections) {
        CheckoutData checkoutData = resolveCheckoutData(showtimeId, selectedSeatIds, userEmail, true,
                promoCode, foodSelections);

        Booking booking = new Booking();
        booking.setUser(checkoutData.getUser());

        if (checkoutData.getUser() == null) {
            booking.setGuestName(guestName);
            booking.setGuestPhone(guestPhone);
            if (guestEmail != null && !guestEmail.isBlank()) {
                booking.setGuestEmail(guestEmail.trim());
            }
        }

        booking.setShowtime(checkoutData.getShowtime());
        booking.setTotalAmount(checkoutData.getTotalAmount());
        booking.setServiceFee(checkoutData.getServiceFee());
        booking.setTaxAmount(checkoutData.getTaxAmount());
        booking.setDiscountAmount(checkoutData.getDiscountAmount());
        booking.setPromoCode(checkoutData.getPromoCode());
        booking.setPaymentMethod(paymentMethod != null ? paymentMethod : "CARD");
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookedAt(LocalDateTime.now());
        Booking savedBooking = bookingRepository.save(booking);

        // Save food items
        saveFoodItems(savedBooking, foodSelections);

        // Record promo usage
        if (promoCode != null && !promoCode.isBlank()) {
            promoCodeService.recordUsage(promoCode);
        }

        paymentService.processMockPayment(savedBooking);
        List<Ticket> tickets = ticketService.issueTickets(savedBooking, checkoutData.getSelectedSeats());

        // Award points to registered user
        if (savedBooking.getUser() != null) {
            int points = checkoutData.getTotalAmount().intValue();
            User user = savedBooking.getUser();
            user.setPoints(user.getPoints() + points);
            userRepository.save(user);
        }

        emailService.sendBookingConfirmation(savedBooking, tickets);
        return savedBooking;
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

    @Transactional
    public void cancelBooking(Long bookingId, String userEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getUser() == null || !booking.getUser().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("You are not authorized to cancel this booking.");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalArgumentException("Booking is already cancelled.");
        }

        // Enforce cancellation window from system settings
        int cancellationHours = systemSettingService.getInt(
                SystemSettingService.CANCELLATION_HOURS, DEFAULT_CANCELLATION_WINDOW_HOURS);
        LocalDateTime showtimeStart = LocalDateTime.of(
                booking.getShowtime().getShowDate(),
                booking.getShowtime().getShowTime() != null ? booking.getShowtime().getShowTime() : LocalTime.MIDNIGHT
        );
        if (LocalDateTime.now().isAfter(showtimeStart.minusHours(cancellationHours))) {
            throw new IllegalArgumentException(
                    "Cancellations are only allowed more than " + cancellationHours +
                    " hours before the showtime.");
        }

        // Mark all tickets as cancelled and free up seats
        List<Ticket> tickets = ticketRepository.findByBooking(booking);
        for (Ticket ticket : tickets) {
            ticket.setStatus(TicketStatus.CANCELLED);
            showtimeSeatRepository
                    .findByShowtimeAndSeat(booking.getShowtime(), ticket.getSeat())
                    .ifPresent(ss -> {
                        ss.setStatus(ShowtimeSeatStatus.AVAILABLE);
                        showtimeSeatRepository.save(ss);
                    });
        }
        ticketRepository.saveAll(tickets);
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        emailService.sendCancellationNotice(booking);
    }

    @Transactional
    public void adminCancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalArgumentException("Booking is already cancelled.");
        }
        List<Ticket> tickets = ticketRepository.findByBooking(booking);
        for (Ticket ticket : tickets) {
            ticket.setStatus(TicketStatus.CANCELLED);
            showtimeSeatRepository
                    .findByShowtimeAndSeat(booking.getShowtime(), ticket.getSeat())
                    .ifPresent(ss -> {
                        ss.setStatus(ShowtimeSeatStatus.AVAILABLE);
                        showtimeSeatRepository.save(ss);
                    });
        }
        ticketRepository.saveAll(tickets);
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
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
        List<Booking> bookings = bookingRepository.findByUser(user);
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

        int normalSeatCount = 0;
        List<SeatCode> coupleSeatCodes = new ArrayList<>();

        for (ShowtimeSeat seat : selectedSeats) {
            if (!seat.getShowtime().getId().equals(showtimeId)) {
                throw new IllegalArgumentException("Selected seats must belong to the chosen showtime.");
            }
            if (seat.getStatus() != ShowtimeSeatStatus.AVAILABLE) {
                throw new IllegalArgumentException("Some seats were already booked. Please reselect seats.");
            }
            if (seat.getSeat().getSeatType() == SeatType.COUPLE) {
                coupleSeatCodes.add(parseSeatCode(seat.getSeat().getSeatCode()));
            } else {
                normalSeatCount++;
            }
        }

        validateCouplePairs(coupleSeatCodes);

        // Base seat price
        BigDecimal base = BigDecimal.ZERO;
        if (normalSeatCount > 0) {
            base = base.add(showtime.getNormalPrice().multiply(BigDecimal.valueOf(normalSeatCount)));
        }
        if (!coupleSeatCodes.isEmpty()) {
            base = base.add(showtime.getCouplePrice().multiply(BigDecimal.valueOf(coupleSeatCodes.size() / 2)));
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
        if (foodSelections != null && !foodSelections.isEmpty()) {
            for (Map.Entry<Long, Integer> entry : foodSelections.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) continue;
                FoodItem item = foodItemService.findById(entry.getKey());
                foodTotal = foodTotal.add(item.getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
            }
        }

        // Promo discount (applied to subtotal before tax, not on fees or food)
        BigDecimal discountAmount = BigDecimal.ZERO;
        String appliedPromo = null;
        if (promoCode != null && !promoCode.isBlank()) {
            int discountPct = promoCodeService.validate(promoCode);
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
                base, morningDiscount, weekendSurcharge, serviceFee, taxAmount,
                discountAmount, total, appliedPromo, availableFoodItems, foodSelections);
    }

    private static final Pattern SEAT_CODE_PATTERN = Pattern.compile("^([A-Za-z]+)(\\d+)$");

    private record SeatCode(String row, int number) {}

    private SeatCode parseSeatCode(String seatCode) {
        if (seatCode == null) {
            throw new IllegalArgumentException("Couple seats must be selected in pairs.");
        }
        Matcher matcher = SEAT_CODE_PATTERN.matcher(seatCode.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Couple seats must be selected in pairs.");
        }
        return new SeatCode(matcher.group(1).toUpperCase(), Integer.parseInt(matcher.group(2)));
    }

    private void validateCouplePairs(List<SeatCode> coupleSeats) {
        if (coupleSeats.isEmpty()) return;
        if (coupleSeats.size() % 2 != 0) {
            throw new IllegalArgumentException("Couple seats must be selected in pairs.");
        }
        Map<String, List<Integer>> numbersByRow = new HashMap<>();
        for (SeatCode seat : coupleSeats) {
            numbersByRow.computeIfAbsent(seat.row(), ignored -> new ArrayList<>()).add(seat.number());
        }
        for (List<Integer> rowNumbers : numbersByRow.values()) {
            rowNumbers.sort(Comparator.naturalOrder());
            if (rowNumbers.size() % 2 != 0) {
                throw new IllegalArgumentException("Couple seats must be selected in pairs.");
            }
            for (int i = 0; i < rowNumbers.size(); i += 2) {
                int first = rowNumbers.get(i);
                int second = rowNumbers.get(i + 1);
                if (second != first + 1 || (first % 2 == 0)) {
                    throw new IllegalArgumentException("Couple seats must be selected in pairs.");
                }
            }
        }
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
