package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import org.springframework.http.HttpStatus;
import org.mockito.ArgumentCaptor;
import co.tkm.ticketmanagementsystem.repository.TicketRepository;
import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.enums.TicketStatus;
import co.tkm.ticketmanagementsystem.enums.BookingStatus;

import co.tkm.ticketmanagementsystem.dto.CheckoutData;
import co.tkm.ticketmanagementsystem.enums.Role;
import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.model.FoodItem;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.BookingFoodItemRepository;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private ShowtimeRepository showtimeRepository;
    @Mock private ShowtimeSeatRepository showtimeSeatRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentService paymentService;
    @Mock private TicketService ticketService;
    @Mock private EmailService emailService;
    @Mock private PromoCodeService promoCodeService;
    @Mock private FoodItemService foodItemService;
    @Mock private BookingFoodItemRepository bookingFoodItemRepository;
    @Mock private SystemSettingService systemSettingService;
    @Mock private PaymentGateway paymentGateway;
    @Mock private SeatHoldService seatHoldService;
    @Mock private SeatRealtimeService seatRealtimeService;
    @Mock private TicketRepository ticketRepository;

    @InjectMocks
    private BookingService bookingService;

    private User user;
    private Showtime showtime;

    @BeforeEach
    void setUp() {
        user = new User(1L, "Test User", "user@test.com", null, "x", Role.CUSTOMER, 0, co.tkm.ticketmanagementsystem.enums.UserStatus.ACTIVE);

        Movie movie = new Movie();
        movie.setId(2L);
        movie.setTitle("Movie");

        Hall hall = new Hall();
        hall.setId(3L);
        hall.setName("Hall A");

        showtime = new Showtime();
        showtime.setId(10L);
        showtime.setMovie(movie);
        showtime.setHall(hall);
        showtime.setShowDate(LocalDate.now());
        showtime.setShowTime(LocalTime.NOON);
        showtime.setNormalPrice(new BigDecimal("5.00"));
        showtime.setCouplePrice(new BigDecimal("8.00"));
    }

    @Test
    void buildCheckout_calculatesTotalCorrectly() {
        ShowtimeSeat normalSeat = seat(100L, SeatType.NORMAL, ShowtimeSeatStatus.AVAILABLE, "C1");
        ShowtimeSeat coupleSeatA = seat(101L, SeatType.COUPLE, ShowtimeSeatStatus.AVAILABLE, "A1");
        ShowtimeSeat coupleSeatB = seat(102L, SeatType.COUPLE, ShowtimeSeatStatus.AVAILABLE, "A2");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(showtimeRepository.findById(10L)).thenReturn(Optional.of(showtime));
        when(showtimeSeatRepository.findAllById(List.of(100L, 101L, 102L)))
                .thenReturn(List.of(normalSeat, coupleSeatA, coupleSeatB));
        when(foodItemService.findAvailable()).thenReturn(Collections.emptyList());
        when(systemSettingService.getDecimal(anyString(), any())).thenAnswer(inv -> inv.getArgument(1));

        CheckoutData checkout = bookingService.buildCheckout(10L, List.of(100L, 101L, 102L), "user@test.com", null);

        // base: 5.00 (normal) + 8.00 (1 couple pair) = 13.00
        // weekend: +10% if today is Sat/Sun else 0
        // tax: 10% of (base + weekend)
        // fee: 1.50
        // total > 13.00
        assertTrue(checkout.getTotalAmount().compareTo(new BigDecimal("13.00")) > 0);
    }

    @Test
    void buildCheckout_rejectsOddCoupleSeatSelection() {
        ShowtimeSeat coupleSeat = seat(200L, SeatType.COUPLE, ShowtimeSeatStatus.AVAILABLE, "A1");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(showtimeRepository.findById(10L)).thenReturn(Optional.of(showtime));
        when(showtimeSeatRepository.findAllById(List.of(200L))).thenReturn(List.of(coupleSeat));

        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.buildCheckout(10L, List.of(200L), "user@test.com", null));
        org.junit.jupiter.api.Assertions.assertEquals("Couple seats must be selected in pairs.", ex.getMessage());
        org.junit.jupiter.api.Assertions.assertEquals("COUPLE_PAIR_INVALID", ex.getCode());
    }

    @Test
    void buildCheckout_rejectsInvalidCouplePairCombination() {
        ShowtimeSeat coupleSeatA1 = seat(210L, SeatType.COUPLE, ShowtimeSeatStatus.AVAILABLE, "A1");
        ShowtimeSeat coupleSeatA3 = seat(211L, SeatType.COUPLE, ShowtimeSeatStatus.AVAILABLE, "A3");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(showtimeRepository.findById(10L)).thenReturn(Optional.of(showtime));
        when(showtimeSeatRepository.findAllById(List.of(210L, 211L))).thenReturn(List.of(coupleSeatA1, coupleSeatA3));

        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.buildCheckout(10L, List.of(210L, 211L), "user@test.com", null));
        org.junit.jupiter.api.Assertions.assertEquals("Couple seats must be selected in pairs.", ex.getMessage());
    }

    @Test
    void buildCheckout_rejectsBookedSeat() {
        ShowtimeSeat bookedSeat = seat(300L, SeatType.NORMAL, ShowtimeSeatStatus.BOOKED, "C3");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(showtimeRepository.findById(10L)).thenReturn(Optional.of(showtime));
        when(showtimeSeatRepository.findAllById(List.of(300L))).thenReturn(List.of(bookedSeat));

        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.buildCheckout(10L, List.of(300L), "user@test.com", null));
        org.junit.jupiter.api.Assertions.assertEquals("Some seats were already booked. Please reselect seats.", ex.getMessage());
        org.junit.jupiter.api.Assertions.assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("SEAT_BOOKED", ex.getCode());
    }

    // ---- Analytics 3.4 price stack. Normal 5.00, couple 8.00 per pair; default rates
    // (morning 10%, weekend 10%, tax 10%, fee 1.50). 2026-10-07 is a Wednesday,
    // 2026-10-04 a Sunday.

    /** Settings the next {@link #quote} reads instead of the service defaults. */
    private final Map<String, BigDecimal> rateOverrides = new java.util.HashMap<>();

    private CheckoutData quote(LocalDate date, LocalTime time, List<ShowtimeSeat> seats,
                               String promo, Map<Long, Integer> food) {
        showtime.setShowDate(date);
        showtime.setShowTime(time);
        List<Long> ids = seats.stream().map(ShowtimeSeat::getId).toList();
        when(showtimeRepository.findById(10L)).thenReturn(Optional.of(showtime));
        when(showtimeSeatRepository.findAllById(ids)).thenReturn(seats);
        lenient().when(foodItemService.findAvailable()).thenReturn(Collections.emptyList());
        lenient().when(systemSettingService.getDecimal(anyString(), any()))
                .thenAnswer(inv -> rateOverrides.getOrDefault(inv.<String>getArgument(0), inv.getArgument(1)));
        return bookingService.buildCheckout(10L, ids, null, promo, food);
    }

    private List<ShowtimeSeat> twoNormalSeats() {
        return List.of(seat(400L, SeatType.NORMAL, ShowtimeSeatStatus.AVAILABLE, "D1"),
                seat(401L, SeatType.NORMAL, ShowtimeSeatStatus.AVAILABLE, "D2"));
    }

    private static BigDecimal money(String v) {
        return new BigDecimal(v);
    }

    @Test
    void quote_weekdayAfternoonTwoNormalSeats() {
        CheckoutData q = quote(LocalDate.of(2026, 10, 7), LocalTime.of(14, 0), twoNormalSeats(), null, Map.of());

        assertThat(q.getBaseAmount()).isEqualByComparingTo("10.00");
        assertThat(q.getMorningDiscount()).isEqualByComparingTo("0");
        assertThat(q.getWeekendSurcharge()).isEqualByComparingTo("0");
        assertThat(q.getSubtotal()).isEqualByComparingTo("10.00");
        assertThat(q.getTaxAmount()).isEqualByComparingTo("1.00");
        assertThat(q.getServiceFee()).isEqualByComparingTo("1.50");
        assertThat(q.getTotalAmount()).isEqualByComparingTo("12.50");
    }

    @Test
    void quote_morningShowGetsTheDiscount() {
        CheckoutData q = quote(LocalDate.of(2026, 10, 7), LocalTime.of(10, 0), twoNormalSeats(), null, Map.of());

        assertThat(q.getMorningDiscount()).isEqualByComparingTo("1.00");
        assertThat(q.getSubtotal()).isEqualByComparingTo("9.00");
        assertThat(q.getTaxAmount()).isEqualByComparingTo("0.90");
        assertThat(q.getTotalAmount()).isEqualByComparingTo("11.40");
    }

    @Test
    void quote_sundayMorningSurchargeIsOnThePostMorningBase() {
        CheckoutData q = quote(LocalDate.of(2026, 10, 4), LocalTime.of(10, 0), twoNormalSeats(), null, Map.of());

        assertThat(q.getMorningDiscount()).isEqualByComparingTo("1.00");
        assertThat(q.getWeekendSurcharge()).isEqualByComparingTo("0.90"); // 10% of 9.00, not of 10.00
        assertThat(q.getSubtotal()).isEqualByComparingTo("9.90");
        assertThat(q.getTaxAmount()).isEqualByComparingTo("0.99");
        assertThat(q.getTotalAmount()).isEqualByComparingTo("12.39");
    }

    @Test
    void quote_promoReducesSubtotalOnlyNotFoodTaxOrFee() {
        FoodItem popcorn = new FoodItem();
        popcorn.setId(7L);
        popcorn.setName("Popcorn");
        popcorn.setPrice(money("3.00"));
        popcorn.setAvailable(true);
        when(foodItemService.findById(7L)).thenReturn(popcorn);
        when(promoCodeService.validateWithSubtotal(eq("save20"), any())).thenReturn(20);

        CheckoutData q = quote(LocalDate.of(2026, 10, 7), LocalTime.of(14, 0), twoNormalSeats(), "save20",
                Map.of(7L, 2));

        assertThat(q.getDiscountAmount()).isEqualByComparingTo("2.00"); // 20% of 10.00
        assertThat(q.getTaxAmount()).isEqualByComparingTo("1.00");      // still on the undiscounted subtotal
        assertThat(q.getFoodTotal()).isEqualByComparingTo("6.00");
        assertThat(q.getFoodLines()).singleElement().satisfies(line -> {
            assertThat(line.name()).isEqualTo("Popcorn");
            assertThat(line.quantity()).isEqualTo(2);
            assertThat(line.lineTotal()).isEqualByComparingTo("6.00");
        });
        assertThat(q.getTotalAmount()).isEqualByComparingTo("16.50");   // 10 - 2 + 1 + 1.5 + 6
        assertThat(q.getPromoCode()).isEqualTo("SAVE20");
    }

    @Test
    void quote_promoUnderMinPurchaseIsRejectedWithPromoInvalid() {
        when(promoCodeService.validateWithSubtotal(eq("BIG50"), any()))
                .thenThrow(new IllegalArgumentException("Minimum purchase of $50.00 required for this promo code."));

        ApiException ex = assertThrows(ApiException.class,
                () -> quote(LocalDate.of(2026, 10, 7), LocalTime.of(14, 0), twoNormalSeats(), "BIG50", Map.of()));

        assertThat(ex.getCode()).isEqualTo(BookingService.PROMO_INVALID);
        assertThat(ex.getMessage()).startsWith("Minimum purchase");
    }

    @Test
    void quote_promoIsValidatedAgainstTheSubtotal() {
        when(promoCodeService.validateWithSubtotal(eq("TEN"), any())).thenReturn(10);

        quote(LocalDate.of(2026, 10, 7), LocalTime.of(14, 0), twoNormalSeats(), "TEN", Map.of());

        org.mockito.Mockito.verify(promoCodeService).validateWithSubtotal(eq("TEN"),
                org.mockito.ArgumentMatchers.argThat(v -> v.compareTo(money("10.00")) == 0));
    }

    @Test
    void quote_couplePairIsPricedOnce() {
        List<ShowtimeSeat> pair = List.of(seat(500L, SeatType.COUPLE, ShowtimeSeatStatus.AVAILABLE, "E1"),
                seat(501L, SeatType.COUPLE, ShowtimeSeatStatus.AVAILABLE, "E2"));

        CheckoutData q = quote(LocalDate.of(2026, 10, 7), LocalTime.of(14, 0), pair, null, Map.of());

        assertThat(q.getBaseAmount()).isEqualByComparingTo("8.00");
        assertThat(q.getTotalAmount()).isEqualByComparingTo("10.30");
    }

    @Test
    void quote_ratesComeFromSystemSettings() {
        rateOverrides.put(SystemSettingService.TAX_RATE, money("0.20"));

        CheckoutData q = quote(LocalDate.of(2026, 10, 7), LocalTime.of(14, 0), twoNormalSeats(), null, Map.of());

        assertThat(q.getTaxAmount()).isEqualByComparingTo("2.00");
        assertThat(q.getTotalAmount()).isEqualByComparingTo("13.50");
    }

    @Test
    void quote_rejectsUnavailableFood() {
        FoodItem soldOut = new FoodItem();
        soldOut.setId(8L);
        soldOut.setName("Nachos");
        soldOut.setPrice(money("4.00"));
        soldOut.setAvailable(false);
        when(foodItemService.findById(8L)).thenReturn(soldOut);

        assertThrows(IllegalArgumentException.class,
                () -> quote(LocalDate.of(2026, 10, 7), LocalTime.of(14, 0), twoNormalSeats(), null, Map.of(8L, 1)));
    }

    // ---- Confirm and pay (analytics 3.5 / 6.3).

    private static final String SESSION = "3f2b8c1e-6a4d-4c3b-9e2f-1a2b3c4d5e6f";

    /** Stubs a weekday-afternoon showtime with [seats] locked for update and the hold owned. */
    private List<Long> lockSeats(List<ShowtimeSeat> seats) {
        showtime.setShowDate(LocalDate.of(2026, 10, 7));
        showtime.setShowTime(LocalTime.of(14, 0));
        List<Long> ids = seats.stream().map(ShowtimeSeat::getId).toList();
        when(showtimeRepository.findById(10L)).thenReturn(Optional.of(showtime));
        when(showtimeSeatRepository.findAllByShowtimeIdAndIdsForUpdate(10L, ids)).thenReturn(seats);
        lenient().when(foodItemService.findAvailable()).thenReturn(Collections.emptyList());
        lenient().when(systemSettingService.getDecimal(anyString(), any())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(seatHoldService.hasActiveConflict(ids, SESSION)).thenReturn(false);
        lenient().when(seatHoldService.ownsActiveHolds(ids, SESSION)).thenReturn(true);
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        return ids;
    }

    /** Lets the mocked TicketService behave like the real one (one ACTIVE ticket per seat, seats BOOKED). */
    private void realTicketIssuing() {
        TicketRepository ticketRepository = org.mockito.Mockito.mock(TicketRepository.class);
        when(ticketRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        TicketService real = new TicketService(ticketRepository, showtimeSeatRepository);
        when(ticketService.issueTickets(any(), any()))
                .thenAnswer(inv -> real.issueTickets(inv.getArgument(0), inv.getArgument(1)));
    }

    @Test
    void confirm_paidIssuesTicketsBooksSeatsRecordsPromoAndAddsPoints() {
        List<ShowtimeSeat> seats = twoNormalSeats();
        List<Long> ids = lockSeats(seats);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(promoCodeService.validateWithSubtotal(eq("SAVE10"), any())).thenReturn(10);
        when(paymentGateway.charge(any(), eq("CARD"))).thenReturn(PaymentGateway.PaymentResult.success());
        realTicketIssuing();

        BookingService.Confirmation result = bookingService.confirmCheckout(10L, ids, "user@test.com", SESSION,
                null, null, null, "SAVE10", "CARD", Map.of());

        assertThat(result.booking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.booking().getTotalAmount()).isEqualByComparingTo("11.50"); // 10 - 1 + 1 + 1.5
        assertThat(result.tickets()).hasSize(2).allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(TicketStatus.ACTIVE));
        assertThat(seats).allSatisfy(ss -> assertThat(ss.getStatus()).isEqualTo(ShowtimeSeatStatus.BOOKED));
        assertThat(user.getPoints()).isEqualTo(11);
        verify(promoCodeService).recordUsage("SAVE10");
        verify(seatHoldService).releaseHolds(SESSION);
        verify(seatRealtimeService).publishBooked(10L, ids);
        verify(emailService).sendBookingConfirmation(result.booking(), result.tickets());
        verify(paymentService).record(any(), eq("CARD"), eq(PaymentGateway.PaymentResult.success()));
    }

    @Test
    void confirm_paymentFailureCancelsTheBookingAndChangesNothingElse() {
        List<ShowtimeSeat> seats = twoNormalSeats();
        List<Long> ids = lockSeats(seats);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(promoCodeService.validateWithSubtotal(eq("SAVE10"), any())).thenReturn(10);
        when(paymentGateway.charge(any(), any())).thenReturn(PaymentGateway.PaymentResult.failure("Card declined."));

        PaymentFailedException ex = assertThrows(PaymentFailedException.class, () -> bookingService.confirmCheckout(
                10L, ids, "user@test.com", SESSION, null, null, null, "SAVE10", "CARD", Map.of()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(ex.getCode()).isEqualTo(PaymentFailedException.PAYMENT_FAILED);
        assertThat(ex.getMessage()).isEqualTo("Card declined.");
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(seats).allSatisfy(ss -> assertThat(ss.getStatus()).isEqualTo(ShowtimeSeatStatus.AVAILABLE));
        assertThat(user.getPoints()).isZero();
        verify(ticketService, never()).issueTickets(any(), any());
        verify(promoCodeService, never()).recordUsage(any());
        verify(seatHoldService, never()).releaseHolds(any());
        verify(seatRealtimeService, never()).publishBooked(any(), any());
        verify(emailService, never()).sendBookingConfirmation(any(), any());
        verify(paymentService).record(any(), eq("CARD"), eq(PaymentGateway.PaymentResult.failure("Card declined.")));
    }

    @Test
    void confirm_alreadyBookedSeatIsSeatBookedAndSavesNoBooking() {
        List<ShowtimeSeat> seats = List.of(seat(400L, SeatType.NORMAL, ShowtimeSeatStatus.BOOKED, "D1"));
        List<Long> ids = lockSeats(seats);

        ApiException ex = assertThrows(ApiException.class, () -> bookingService.confirmCheckout(
                10L, ids, null, SESSION, "Dara", "012", null, null, "CARD", Map.of()));

        assertThat(ex.getCode()).isEqualTo(SeatHoldService.SEAT_BOOKED);
        verify(bookingRepository, never()).save(any());
        verify(paymentGateway, never()).charge(any(), any());
    }

    @Test
    void confirm_withoutTheHoldIsHoldExpired() {
        List<Long> ids = lockSeats(twoNormalSeats());
        when(seatHoldService.ownsActiveHolds(ids, SESSION)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> bookingService.confirmCheckout(
                10L, ids, null, SESSION, "Dara", "012", null, null, "CARD", Map.of()));

        assertThat(ex.getCode()).isEqualTo(SeatHoldService.HOLD_EXPIRED);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirm_guestStoresContactDetailsAndEarnsNoPoints() {
        List<Long> ids = lockSeats(twoNormalSeats());
        when(paymentGateway.charge(any(), any())).thenReturn(PaymentGateway.PaymentResult.success());
        realTicketIssuing();

        BookingService.Confirmation result = bookingService.confirmCheckout(10L, ids, null, SESSION,
                " Dara ", "012345", " dara@example.com ", null, null, Map.of());

        Booking booking = result.booking();
        assertThat(booking.getUser()).isNull();
        assertThat(booking.getGuestName()).isEqualTo("Dara");
        assertThat(booking.getGuestPhone()).isEqualTo("012345");
        assertThat(booking.getGuestEmail()).isEqualTo("dara@example.com");
        assertThat(booking.getPaymentMethod()).isEqualTo("CARD");
        verify(userRepository, never()).save(any());
    }

    @Test
    void confirm_guestWithoutPhoneIsRejected() {
        List<Long> ids = lockSeats(twoNormalSeats());

        assertThrows(IllegalArgumentException.class, () -> bookingService.confirmCheckout(
                10L, ids, null, SESSION, "Dara", " ", null, null, "CARD", Map.of()));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirm_blockedUserIsAccountBlocked() {
        List<Long> ids = lockSeats(twoNormalSeats());
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        ApiException ex = assertThrows(ApiException.class, () -> bookingService.confirmCheckout(
                10L, ids, "user@test.com", SESSION, null, null, null, null, "CARD", Map.of()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo(AuthService.ACCOUNT_BLOCKED);
        verify(bookingRepository, never()).save(any());
    }

    // ---- Customer cancel (analytics 3.6, invariant 7).

    /** A CONFIRMED booking owned by [owner] whose show starts [hoursAhead] from now, with booked [seats]. */
    private Booking bookingWithSeats(User owner, long hoursAhead, List<ShowtimeSeat> seats) {
        java.time.LocalDateTime start = java.time.LocalDateTime.now().plusHours(hoursAhead);
        showtime.setShowDate(start.toLocalDate());
        showtime.setShowTime(start.toLocalTime());
        Booking booking = new Booking();
        booking.setId(50L);
        booking.setUser(owner);
        booking.setShowtime(showtime);
        booking.setStatus(BookingStatus.CONFIRMED);
        List<Ticket> tickets = new java.util.ArrayList<>();
        for (ShowtimeSeat ss : seats) {
            Ticket t = new Ticket();
            t.setBooking(booking);
            t.setSeat(ss.getSeat());
            t.setStatus(TicketStatus.ACTIVE);
            tickets.add(t);
            lenient().when(showtimeSeatRepository.findByShowtimeAndSeat(showtime, ss.getSeat()))
                    .thenReturn(Optional.of(ss));
        }
        when(bookingRepository.findById(50L)).thenReturn(Optional.of(booking));
        lenient().when(ticketRepository.findByBooking(booking)).thenReturn(tickets);
        lenient().when(systemSettingService.getInt(eq(SystemSettingService.CANCELLATION_HOURS), anyInt()))
                .thenAnswer(inv -> inv.getArgument(1));
        return booking;
    }

    private List<ShowtimeSeat> twoBookedSeats() {
        return List.of(seat(400L, SeatType.NORMAL, ShowtimeSeatStatus.BOOKED, "D1"),
                seat(401L, SeatType.NORMAL, ShowtimeSeatStatus.BOOKED, "D2"));
    }

    @Test
    void cancel_ownerOutsideTheWindowFreesSeatsAndPublishesRelease() {
        List<ShowtimeSeat> seats = twoBookedSeats();
        Booking booking = bookingWithSeats(user, 48, seats);

        bookingService.cancelBooking(50L, "user@test.com");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(ticketRepository.findByBooking(booking))
                .allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(TicketStatus.CANCELLED));
        assertThat(seats).allSatisfy(ss -> assertThat(ss.getStatus()).isEqualTo(ShowtimeSeatStatus.AVAILABLE));
        verify(seatRealtimeService).publishReleased(10L, List.of(400L, 401L), null);
        verify(emailService).sendCancellationNotice(booking);
        // Points and promo usage are not reversed in this sprint.
        assertThat(user.getPoints()).isZero();
        verify(userRepository, never()).save(any());
        verify(promoCodeService, never()).recordUsage(any());
    }

    @Test
    void cancel_byAStrangerIsNotOwner() {
        List<ShowtimeSeat> seats = twoBookedSeats();
        Booking booking = bookingWithSeats(user, 48, seats);

        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.cancelBooking(50L, "stranger@test.com"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo(BookingService.NOT_OWNER);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(seats).allSatisfy(ss -> assertThat(ss.getStatus()).isEqualTo(ShowtimeSeatStatus.BOOKED));
        verify(seatRealtimeService, never()).publishReleased(any(), any(), any());
    }

    @Test
    void cancel_guestBookingCannotBeCancelledByAnyUser() {
        Booking booking = bookingWithSeats(null, 48, twoBookedSeats());
        booking.setGuestName("Dara");

        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.cancelBooking(50L, "user@test.com"));

        assertThat(ex.getCode()).isEqualTo(BookingService.NOT_OWNER);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void cancel_alreadyCancelledIsConflict() {
        Booking booking = bookingWithSeats(user, 48, twoBookedSeats());
        booking.setStatus(BookingStatus.CANCELLED);

        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.cancelBooking(50L, "user@test.com"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(BookingService.ALREADY_CANCELLED);
    }

    @Test
    void cancel_insideTheWindowIsCancelWindowAndSeatsStayBooked() {
        List<ShowtimeSeat> seats = twoBookedSeats();
        Booking booking = bookingWithSeats(user, 1, seats); // default window is 2 hours

        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.cancelBooking(50L, "user@test.com"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(BookingService.CANCEL_WINDOW);
        assertThat(ex.getMessage()).isEqualTo("Cancellations are only allowed more than 2 hours before the showtime.");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(seats).allSatisfy(ss -> assertThat(ss.getStatus()).isEqualTo(ShowtimeSeatStatus.BOOKED));
        verify(ticketRepository, never()).saveAll(any());
        verify(seatRealtimeService, never()).publishReleased(any(), any(), any());
    }

    @Test
    void cancel_windowLengthComesFromSystemSettings() {
        bookingWithSeats(user, 5, twoBookedSeats());
        when(systemSettingService.getInt(eq(SystemSettingService.CANCELLATION_HOURS), anyInt()))
                .thenReturn(6);

        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.cancelBooking(50L, "user@test.com"));

        assertThat(ex.getCode()).isEqualTo(BookingService.CANCEL_WINDOW);
    }

    @Test
    void myBookings_areReadNewestFirst() {
        Booking newer = new Booking();
        newer.setShowtime(showtime);
        Booking older = new Booking();
        older.setShowtime(showtime);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(bookingRepository.findByUserOrderByBookedAtDesc(user)).thenReturn(List.of(newer, older));

        assertThat(bookingService.getBookingsForUser("user@test.com")).containsExactly(newer, older);
        verify(bookingRepository, never()).findByUser(any());
    }

    private ShowtimeSeat seat(Long id, SeatType seatType, ShowtimeSeatStatus status, String code) {
        Seat seat = new Seat();
        seat.setId(id + 1000);
        seat.setSeatType(seatType);
        seat.setSeatCode(code);

        ShowtimeSeat showtimeSeat = new ShowtimeSeat();
        showtimeSeat.setId(id);
        showtimeSeat.setSeat(seat);
        showtimeSeat.setShowtime(showtime);
        showtimeSeat.setStatus(status);
        return showtimeSeat;
    }
}
