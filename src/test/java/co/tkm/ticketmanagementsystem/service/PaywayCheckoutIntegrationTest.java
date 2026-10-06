package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.enums.PaymentStatus;
import co.tkm.ticketmanagementsystem.enums.Role;
import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Payment;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.model.SeatHold;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.HallRepository;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import co.tkm.ticketmanagementsystem.repository.PaymentRepository;
import co.tkm.ticketmanagementsystem.repository.SeatHoldRepository;
import co.tkm.ticketmanagementsystem.repository.SeatRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import co.tkm.ticketmanagementsystem.repository.TicketRepository;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 9 against H2: a PayWay start leaves only a PENDING booking, an approval runs the
 * paid path once, a decline leaves the seats available, and the callback needs its HMAC.
 * {@link PaywayClient} is the only mock; nothing reaches PayWay.
 */
@SpringBootTest(properties = {
        "payway.base-url=https://checkout-sandbox.payway.com.kh",
        "payway.merchant-id=test-merchant",
        "payway.api-key=test-api-key",
        "payway.return-url=https://example.test/api/payway/callback"})
class PaywayCheckoutIntegrationTest {

    @Autowired private PaywayService paywayService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private HallRepository hallRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private ShowtimeRepository showtimeRepository;
    @Autowired private ShowtimeSeatRepository showtimeSeatRepository;
    @Autowired private SeatHoldRepository seatHoldRepository;

    @MockitoBean private PaywayClient paywayClient;
    @MockitoBean private SeatRealtimeService seatRealtimeService;
    @MockitoBean private EmailService emailService;

    private Long showtimeId;
    private List<Long> seatIds;
    private String session;
    private User user;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Movie movie = new Movie();
        movie.setTitle("PayWay IT " + suffix);
        movie.setGenre("Drama");
        movie.setDurationMinutes(120);
        movie.setStatus(MovieStatus.NOW_SHOWING);
        movie.setReleaseDate(LocalDate.now());
        movie = movieRepository.save(movie);

        Hall hall = new Hall();
        hall.setName("PW Hall " + suffix);
        hall.setCity("Phnom Penh");
        hall.setCapacity(2);
        hall = hallRepository.save(hall);

        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setHall(hall);
        showtime.setShowDate(LocalDate.now().plusDays(5));
        showtime.setShowTime(LocalTime.of(15, 0));
        showtime.setNormalPrice(new BigDecimal("5.00"));
        showtime.setCouplePrice(new BigDecimal("8.00"));
        showtime = showtimeRepository.save(showtime);
        showtimeId = showtime.getId();

        user = userRepository.save(new User(null, "Dara Sok", "pw-" + suffix + "@test.com", "012345678",
                "x", Role.CUSTOMER, 0, UserStatus.ACTIVE));

        session = UUID.randomUUID().toString();
        seatIds = new ArrayList<>();
        for (String code : List.of("E1", "E2")) {
            Seat seat = new Seat();
            seat.setHall(hall);
            seat.setSeatCode(code);
            seat.setSeatType(SeatType.NORMAL);
            seat.setRowNumber(5);
            seat = seatRepository.save(seat);

            ShowtimeSeat ss = new ShowtimeSeat();
            ss.setShowtime(showtime);
            ss.setSeat(seat);
            ss.setStatus(ShowtimeSeatStatus.AVAILABLE);
            seatIds.add(showtimeSeatRepository.save(ss).getId());
        }
        hold(10);

        when(paywayClient.isConfigured()).thenReturn(true);
        when(paywayClient.purchase(anyMap())).thenReturn(new PaywayClient.PurchaseResult(
                "https://checkout-sandbox.payway.com.kh/abc", "abamobilebank://ababank.com?type=payway&qrcode=x"));
        when(paywayClient.closeTransaction(any())).thenReturn(true);
    }

    private void hold(int minutes) {
        seatHoldRepository.deleteAll(seatHoldRepository.findBySessionId(session));
        for (Long id : seatIds) {
            SeatHold hold = new SeatHold();
            hold.setShowtimeSeatId(id);
            hold.setSessionId(session);
            hold.setExpiresAt(LocalDateTime.now().plusMinutes(minutes).plusSeconds(30));
            seatHoldRepository.save(hold);
        }
    }

    private PaywayService.Started start() {
        return paywayService.start(showtimeId, seatIds, user.getEmail(), session,
                null, null, null, null, Map.of());
    }

    private List<ShowtimeSeat> seats() {
        return showtimeSeatRepository.findAllById(seatIds);
    }

    @Test
    void startWritesPendingAndSendsTheServerTotalWithoutTicketsOrBookedSeats() {
        PaywayService.Started started = start();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> fields = ArgumentCaptor.forClass(Map.class);
        verify(paywayClient).purchase(fields.capture());
        Booking booking = bookingRepository.findById(started.bookingId()).orElseThrow();
        Payment payment = paymentRepository.findByTranId(started.tranId()).orElseThrow();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getPaymentMethod()).isEqualTo("ABA_KHQR");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(started.tranId()).hasSizeLessThanOrEqualTo(20);
        assertThat(started.checkoutUrl()).isEqualTo("https://checkout-sandbox.payway.com.kh/abc");
        assertThat(started.amount()).isEqualByComparingTo(booking.getTotalAmount());
        assertThat(fields.getValue())
                .containsEntry("amount", booking.getTotalAmount().setScale(2).toPlainString())
                .containsEntry("currency", "USD")
                .containsEntry("payment_option", "abapay_khqr_deeplink")
                .containsEntry("tran_id", started.tranId())
                .containsEntry("firstname", "Dara")
                .containsEntry("lastname", "Sok")
                .containsEntry("lifetime", "10");
        assertThat(ticketRepository.findByBooking(booking)).isEmpty();
        assertThat(seats()).allSatisfy(ss -> assertThat(ss.getStatus()).isEqualTo(ShowtimeSeatStatus.AVAILABLE));
        assertThat(seatHoldRepository.findActiveHeldSeatIdsBySession(session, LocalDateTime.now()))
                .containsExactlyInAnyOrderElementsOf(seatIds);
    }

    @Test
    void approvedConfirmsOnceAndASecondPollAddsNothing() {
        PaywayService.Started started = start();
        when(paywayClient.checkTransaction(started.tranId())).thenReturn("APPROVED");

        PaywayService.Status first = paywayService.poll(started.tranId(), user.getEmail(), session);
        int pointsAfterFirst = userRepository.findById(user.getId()).orElseThrow().getPoints();
        PaywayService.Status second = paywayService.poll(started.tranId(), user.getEmail(), session);

        Booking booking = bookingRepository.findById(started.bookingId()).orElseThrow();
        assertThat(first.status()).isEqualTo("APPROVED");
        assertThat(second.status()).isEqualTo("APPROVED");
        assertThat(first.confirmation().tickets()).hasSize(2);
        assertThat(second.confirmation().tickets()).extracting("ticketCode")
                .containsExactlyInAnyOrderElementsOf(first.confirmation().tickets().stream()
                        .map(t -> t.getTicketCode()).toList());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(ticketRepository.findByBooking(booking)).hasSize(2);
        assertThat(pointsAfterFirst).isEqualTo(booking.getTotalAmount().intValue());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPoints()).isEqualTo(pointsAfterFirst);
        assertThat(seats()).allSatisfy(ss -> assertThat(ss.getStatus()).isEqualTo(ShowtimeSeatStatus.BOOKED));
        assertThat(paymentRepository.findByTranId(started.tranId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PAID);
        assertThat(seatHoldRepository.findActiveHeldSeatIdsBySession(session, LocalDateTime.now())).isEmpty();
    }

    @Test
    void declinedCancelsTheBookingAndLeavesTheSeatsAvailable() {
        PaywayService.Started started = start();
        when(paywayClient.checkTransaction(started.tranId())).thenReturn("DECLINED");

        PaywayService.Status status = paywayService.poll(started.tranId(), user.getEmail(), session);

        Booking booking = bookingRepository.findById(started.bookingId()).orElseThrow();
        assertThat(status.status()).isEqualTo("DECLINED");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(paymentRepository.findByTranId(started.tranId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(ticketRepository.findByBooking(booking)).isEmpty();
        assertThat(seats()).allSatisfy(ss -> assertThat(ss.getStatus()).isEqualTo(ShowtimeSeatStatus.AVAILABLE));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPoints()).isZero();
        verify(paywayClient).closeTransaction(started.tranId());

        // A later approval of the closed payment never confirms it.
        when(paywayClient.checkTransaction(started.tranId())).thenReturn("APPROVED");
        assertThat(paywayService.poll(started.tranId(), user.getEmail(), session).status()).isEqualTo("DECLINED");
        assertThat(bookingRepository.findById(started.bookingId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void closeWhilePendingClosesAtPaywayAndKeepsTheHold() {
        PaywayService.Started started = start();
        when(paywayClient.checkTransaction(started.tranId())).thenReturn("PENDING");

        PaywayService.Status status = paywayService.close(started.tranId(), user.getEmail(), session);

        assertThat(status.status()).isEqualTo("CANCELLED");
        verify(paywayClient).closeTransaction(started.tranId());
        assertThat(bookingRepository.findById(started.bookingId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(seats()).allSatisfy(ss -> assertThat(ss.getStatus()).isEqualTo(ShowtimeSeatStatus.AVAILABLE));
        assertThat(seatHoldRepository.findActiveHeldSeatIdsBySession(session, LocalDateTime.now()))
                .containsExactlyInAnyOrderElementsOf(seatIds);
    }

    @Test
    void pollByAnotherCallerIsNotOwner() {
        PaywayService.Started started = start();

        ApiException ex = assertThrows(ApiException.class,
                () -> paywayService.poll(started.tranId(), "someone@else.com", UUID.randomUUID().toString()));

        assertThat(ex.getCode()).isEqualTo(BookingService.NOT_OWNER);
    }

    @Test
    void callbackWithABadHmacIsRejectedAndConfirmsNothing() {
        PaywayService.Started started = start();
        String body = "{\"tran_id\":\"" + started.tranId() + "\",\"status\":\"0\",\"payment_amount\":1}";

        ApiException ex = assertThrows(ApiException.class, () -> paywayService.callback(body, "bad-signature"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bookingRepository.findById(started.bookingId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING);
        assertThat(seats()).allSatisfy(ss -> assertThat(ss.getStatus()).isEqualTo(ShowtimeSeatStatus.AVAILABLE));
    }

    @Test
    void callbackWithAGoodHmacConfirms() throws Exception {
        PaywayService.Started started = start();
        String body = "{\"tran_id\":\"" + started.tranId() + "\",\"status\":\"0\",\"payment_amount\":1}";
        String signature = PaywaySigner.callbackSignature(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(body), "test-api-key");

        paywayService.callback(body, signature);
        paywayService.callback(body, signature);

        Booking booking = bookingRepository.findById(started.bookingId()).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(ticketRepository.findByBooking(booking)).hasSize(2);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPoints())
                .isEqualTo(booking.getTotalAmount().intValue());
    }

    @Test
    void holdUnderThreeMinutesIsHoldExpiredAndNeverCallsPurchase() {
        hold(2);
        long bookingsBefore = bookingRepository.countByShowtimeId(showtimeId);

        ApiException ex = assertThrows(ApiException.class, this::start);

        assertThat(ex.getCode()).isEqualTo(SeatHoldService.HOLD_EXPIRED);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(paywayClient, never()).purchase(anyMap());
        assertThat(bookingRepository.countByShowtimeId(showtimeId)).isEqualTo(bookingsBefore);
    }

    @Test
    void purchaseFailureCancelsTheBookingAndKeepsTheHold() {
        when(paywayClient.purchase(anyMap())).thenThrow(new PaywayClient.PaywayException("Wrong hash"));

        PaymentFailedException ex = assertThrows(PaymentFailedException.class, this::start);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(bookingRepository.findAll()).filteredOn(b -> b.getShowtime().getId().equals(showtimeId))
                .singleElement().satisfies(b -> assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED));
        assertThat(seatHoldRepository.findActiveHeldSeatIdsBySession(session, LocalDateTime.now()))
                .containsExactlyInAnyOrderElementsOf(seatIds);
    }
}
