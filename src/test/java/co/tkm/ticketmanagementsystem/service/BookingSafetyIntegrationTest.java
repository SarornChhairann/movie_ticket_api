package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.controller.HoldSessions;
import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.enums.PaymentStatus;
import co.tkm.ticketmanagementsystem.enums.Role;
import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.enums.TicketStatus;
import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Payment;
import co.tkm.ticketmanagementsystem.model.PromoCode;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.HallRepository;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import co.tkm.ticketmanagementsystem.repository.PaymentRepository;
import co.tkm.ticketmanagementsystem.repository.PromoCodeRepository;
import co.tkm.ticketmanagementsystem.repository.SeatHoldRepository;
import co.tkm.ticketmanagementsystem.repository.SeatRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import co.tkm.ticketmanagementsystem.repository.TicketRepository;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 12 against H2: a booking is read only by its owner (G25), the seat cap holds on
 * every entry point (G26), and cancel gives back points and promo use (G27) and leaves a
 * refund trail (G28).
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingSafetyIntegrationTest {

    private static final int CAP = 2;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BookingService bookingService;
    @Autowired private SeatHoldService seatHoldService;
    @Autowired private SystemSettingService systemSettingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PromoCodeRepository promoCodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private HallRepository hallRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private ShowtimeRepository showtimeRepository;
    @Autowired private ShowtimeSeatRepository showtimeSeatRepository;
    @Autowired private SeatHoldRepository seatHoldRepository;
    @Autowired private TicketRepository ticketRepository;

    @MockitoBean private SeatRealtimeService seatRealtimeService;
    @MockitoBean private EmailService emailService;

    private String suffix;
    private String session;
    private User owner;
    private Showtime showtime;
    /** Seat code → showtime seat id: N1–N3 normal, C1–C2 a couple pair. */
    private Map<String, Long> seats;

    @BeforeEach
    void seed() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        session = UUID.randomUUID().toString();
        systemSettingService.set(SystemSettingService.MAX_SEATS_PER_BOOKING, String.valueOf(CAP));

        owner = userRepository.save(new User(null, "Owner " + suffix, "owner-" + suffix + "@test.com",
                null, "x", Role.CUSTOMER, 0, UserStatus.ACTIVE));

        Movie movie = new Movie();
        movie.setTitle("Safety IT " + suffix);
        movie.setGenre("Drama");
        movie.setDurationMinutes(120);
        movie.setStatus(MovieStatus.NOW_SHOWING);
        movie.setReleaseDate(LocalDate.now());
        movie = movieRepository.save(movie);

        Hall hall = new Hall();
        hall.setName("Safety Hall " + suffix);
        hall.setCity("Phnom Penh");
        hall.setCapacity(5);
        hall = hallRepository.save(hall);

        showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setHall(hall);
        showtime.setShowDate(LocalDate.now().plusDays(3));
        showtime.setShowTime(LocalTime.of(19, 0));
        showtime.setNormalPrice(new BigDecimal("5.00"));
        showtime.setCouplePrice(new BigDecimal("8.00"));
        showtime = showtimeRepository.save(showtime);

        seats = new LinkedHashMap<>();
        for (String code : List.of("N1", "N2", "N3", "C1", "C2")) {
            Seat seat = new Seat();
            seat.setHall(hall);
            seat.setSeatCode(code);
            seat.setSeatType(code.startsWith("C") ? SeatType.COUPLE : SeatType.NORMAL);
            seat.setRowNumber(code.startsWith("C") ? 2 : 1);
            seat = seatRepository.save(seat);

            ShowtimeSeat ss = new ShowtimeSeat();
            ss.setShowtime(showtime);
            ss.setSeat(seat);
            ss.setStatus(ShowtimeSeatStatus.AVAILABLE);
            seats.put(code, showtimeSeatRepository.save(ss).getId());
        }
    }

    @AfterEach
    void restoreCap() {
        systemSettingService.set(SystemSettingService.MAX_SEATS_PER_BOOKING,
                String.valueOf(SeatHoldService.DEFAULT_MAX_SEATS));
    }

    private List<Long> ids(String... codes) {
        return java.util.Arrays.stream(codes).map(seats::get).toList();
    }

    private static void assertSeatLimit(Executable call) {
        ApiException ex = assertThrows(ApiException.class, call);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(SeatHoldService.SEAT_LIMIT);
    }

    // ------------------------------------------------------------------ G26 seat cap

    @Test
    void holdAboveTheCapIsSeatLimitAndHoldsNothing() {
        assertSeatLimit(() -> seatHoldService.holdSeatsRealtime(showtime.getId(), ids("N1", "N2", "N3"), session));

        assertThat(seatHoldRepository.findBySessionId(session)).isEmpty();
    }

    @Test
    void holdsOfOneSessionAddUpTowardsTheCap() {
        seatHoldService.holdSeatsRealtime(showtime.getId(), ids("N1", "N2"), session);
        // Holding the same seats again is a refresh, not more seats.
        seatHoldService.holdSeatsRealtime(showtime.getId(), ids("N1", "N2"), session);

        assertSeatLimit(() -> seatHoldService.holdSeatsRealtime(showtime.getId(), ids("N3"), session));
        assertThat(seatHoldRepository.findBySessionId(session)).hasSize(2);
    }

    @Test
    void aCouplePairCountsAsTwoSeats() {
        assertSeatLimit(() -> seatHoldService.holdSeatsRealtime(showtime.getId(), ids("C1", "C2", "N1"), session));

        seatHoldService.holdSeatsRealtime(showtime.getId(), ids("C1", "C2"), session);
        assertThat(seatHoldRepository.findBySessionId(session)).hasSize(2);
    }

    @Test
    void quoteConfirmAndPaywayStartRejectAboveTheCapBeforeWriting() {
        List<Long> three = ids("N1", "N2", "N3");
        long bookingsBefore = bookingRepository.count();

        assertSeatLimit(() -> bookingService.buildCheckout(showtime.getId(), three, null, null, Map.of()));
        assertSeatLimit(() -> bookingService.confirmCheckout(showtime.getId(), three, null, session,
                "Guest", "012345678", null, null, "CARD", Map.of()));
        assertSeatLimit(() -> bookingService.openPaywayBooking(showtime.getId(), three, null, session,
                "Guest", "012345678", null, null, Map.of()));

        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore);
    }

    @Test
    void seatMapTellsTheClientTheCap() throws Exception {
        String body = mvc.perform(get("/bookings/seats/" + showtime.getId()).header(HoldSessions.HEADER, session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).get("maxSeats").asInt()).isEqualTo(CAP);
    }

    // ------------------------------------------------------------------ G25 owner-only read

    @Test
    void confirmStoresTheSessionSoTheSameSessionCanReadTheBooking() throws Exception {
        seatHoldService.holdSeatsRealtime(showtime.getId(), ids("N1"), session);
        BookingService.Confirmation confirmation = bookingService.confirmCheckout(showtime.getId(), ids("N1"),
                null, session, "Guest " + suffix, "012345678", "guest@example.com", null, "CARD", Map.of());
        Long id = confirmation.booking().getId();

        assertThat(bookingRepository.findById(id).orElseThrow().getSessionId()).isEqualTo(session);

        String body = mvc.perform(get("/bookings/confirmation/" + id).header(HoldSessions.HEADER, session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.get("id").asLong()).isEqualTo(id);
        assertThat(json.get("paymentStatus").asText()).isEqualTo("PAID");
    }

    @Test
    void anotherSessionNoSessionAndAMissingIdAreAllTheSame404() throws Exception {
        Long id = confirmedBooking(owner, new BigDecimal("12.50"), null, null).getId();

        assertNotFound(mvc.perform(get("/bookings/confirmation/" + id)
                .header(HoldSessions.HEADER, UUID.randomUUID().toString())).andReturn().getResponse());
        assertNotFound(mvc.perform(get("/bookings/confirmation/" + id)).andReturn().getResponse());
        assertNotFound(mvc.perform(get("/bookings/confirmation/" + Long.MAX_VALUE)
                .header(HoldSessions.HEADER, session)).andReturn().getResponse());
    }

    @Test
    void theOwningUserReadsWithoutTheSessionAndAStrangerCannot() {
        Long id = confirmedBooking(owner, new BigDecimal("12.50"), null, null).getId();

        assertThat(bookingService.findForReader(id, owner.getEmail().toUpperCase(), null).getId()).isEqualTo(id);

        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.findForReader(id, "stranger-" + suffix + "@test.com", UUID.randomUUID().toString()));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo(BookingAccess.BOOKING_NOT_FOUND);
    }

    private void assertNotFound(org.springframework.mock.web.MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(objectMapper.readTree(response.getContentAsString()).get("code").asText())
                .isEqualTo(BookingAccess.BOOKING_NOT_FOUND);
    }

    // ------------------------------------------------------------------ G27 / G28 cancel

    @Test
    void cancelTakesBackThePointsAndThePromoUseAndOwesARefund() {
        owner.setPoints(20);
        owner = userRepository.save(owner);
        PromoCode promo = promo(1);
        Booking booking = confirmedBooking(owner, new BigDecimal("12.50"), promo.getCode(), PaymentStatus.PAID);

        bookingService.cancelBooking(booking.getId(), owner.getEmail());

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getPoints()).isEqualTo(8);
        assertThat(promoCodeRepository.findById(promo.getId()).orElseThrow().getUsedCount()).isZero();
        assertThat(paymentRepository.findByBooking(booking).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUND_PENDING);
    }

    @Test
    void reversalNeverGoesBelowZero() {
        owner.setPoints(5);
        owner = userRepository.save(owner);
        PromoCode promo = promo(0);
        Booking booking = confirmedBooking(owner, new BigDecimal("12.50"), promo.getCode(), null);

        bookingService.adminCancelBooking(booking.getId());

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getPoints()).isZero();
        assertThat(promoCodeRepository.findById(promo.getId()).orElseThrow().getUsedCount()).isZero();
        assertThat(paymentRepository.findByBooking(booking)).isEmpty();
    }

    @Test
    void aBookingThatWasNeverConfirmedGivesNothingBack() {
        owner.setPoints(20);
        owner = userRepository.save(owner);
        Booking booking = confirmedBooking(owner, new BigDecimal("12.50"), null, PaymentStatus.FAILED);
        booking.setStatus(BookingStatus.PENDING);
        bookingRepository.save(booking);

        bookingService.adminCancelBooking(booking.getId());

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getPoints()).isEqualTo(20);
        assertThat(paymentRepository.findByBooking(booking).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void adminMarksAPendingRefundRefundedOnce() {
        Booking booking = confirmedBooking(owner, new BigDecimal("12.50"), null, PaymentStatus.PAID);
        bookingService.adminCancelBooking(booking.getId());

        assertThat(bookingService.getBookingsByPaymentStatus(PaymentStatus.REFUND_PENDING))
                .extracting(Booking::getId).contains(booking.getId());

        bookingService.markRefunded(booking.getId());

        assertThat(paymentRepository.findByBooking(booking).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThrows(IllegalStateException.class, () -> bookingService.markRefunded(booking.getId()));
    }

    // ------------------------------------------------------------------ fixtures

    private PromoCode promo(int usedCount) {
        PromoCode promo = new PromoCode();
        promo.setCode("SAFE" + suffix.toUpperCase());
        promo.setDiscountPercent(10);
        promo.setActive(true);
        promo.setUsedCount(usedCount);
        return promoCodeRepository.save(promo);
    }

    /** A CONFIRMED booking on N1 for [user] with one ACTIVE ticket, and a payment when [paymentStatus] is set. */
    private Booking confirmedBooking(User user, BigDecimal total, String promoCode, PaymentStatus paymentStatus) {
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setShowtime(showtime);
        booking.setTotalAmount(total);
        booking.setPromoCode(promoCode);
        booking.setPaymentMethod("CARD");
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setBookedAt(LocalDateTime.now());
        booking = bookingRepository.save(booking);

        ShowtimeSeat ss = showtimeSeatRepository.findById(seats.get("N1")).orElseThrow();
        ss.setStatus(ShowtimeSeatStatus.BOOKED);
        showtimeSeatRepository.save(ss);
        Ticket ticket = new Ticket();
        ticket.setBooking(booking);
        ticket.setSeat(ss.getSeat());
        ticket.setTicketCode(UUID.randomUUID().toString());
        ticket.setStatus(TicketStatus.ACTIVE);
        ticket.setIssuedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        if (paymentStatus != null) {
            Payment payment = new Payment();
            payment.setBooking(booking);
            payment.setAmount(total);
            payment.setMethod("CARD");
            payment.setStatus(paymentStatus);
            paymentRepository.save(payment);
        }
        return booking;
    }
}
