package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.model.SeatHold;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.HallRepository;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import co.tkm.ticketmanagementsystem.repository.SeatHoldRepository;
import co.tkm.ticketmanagementsystem.repository.SeatRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Invariant 8 against a real (H2) database: two confirms for the same seats
 * serialize on the row lock, and the loser gets "already booked", not a second ticket.
 */
@SpringBootTest
class ConfirmCheckoutIntegrationTest {

    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private HallRepository hallRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private ShowtimeRepository showtimeRepository;
    @Autowired private ShowtimeSeatRepository showtimeSeatRepository;
    @Autowired private SeatHoldRepository seatHoldRepository;

    private Long showtimeId;
    private List<Long> seatIds;
    private String session;

    @BeforeEach
    void seedHeldSeats() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Movie movie = new Movie();
        movie.setTitle("Confirm IT " + suffix);
        movie.setGenre("Drama");
        movie.setDurationMinutes(120);
        movie.setStatus(MovieStatus.NOW_SHOWING);
        movie.setReleaseDate(LocalDate.now());
        movie = movieRepository.save(movie);

        Hall hall = new Hall();
        hall.setName("IT Hall " + suffix);
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

        session = UUID.randomUUID().toString();
        seatIds = new ArrayList<>();
        for (String code : List.of("D1", "D2")) {
            Seat seat = new Seat();
            seat.setHall(hall);
            seat.setSeatCode(code);
            seat.setSeatType(SeatType.NORMAL);
            seat.setRowNumber(4);
            seat = seatRepository.save(seat);

            ShowtimeSeat ss = new ShowtimeSeat();
            ss.setShowtime(showtime);
            ss.setSeat(seat);
            ss.setStatus(ShowtimeSeatStatus.AVAILABLE);
            ss = showtimeSeatRepository.save(ss);
            seatIds.add(ss.getId());

            SeatHold hold = new SeatHold();
            hold.setShowtimeSeatId(ss.getId());
            hold.setSessionId(session);
            hold.setExpiresAt(LocalDateTime.now().plusMinutes(10));
            seatHoldRepository.save(hold);
        }
    }

    private BookingService.Confirmation confirm() {
        return bookingService.confirmCheckout(showtimeId, seatIds, null, session,
                "Dara", "012345678", null, null, "CARD", Map.of());
    }

    @Test
    void secondConfirmOnTheSameSeatsIsSeatBookedAndCreatesNoSecondBooking() {
        BookingService.Confirmation first = confirm();

        ApiException ex = assertThrows(ApiException.class, this::confirm);

        assertThat(ex.getCode()).isEqualTo(SeatHoldService.SEAT_BOOKED);
        assertThat(first.booking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(first.tickets()).hasSize(2);
        assertThat(bookingRepository.countByShowtimeId(showtimeId)).isEqualTo(1);
        assertThat(showtimeSeatRepository.findAllById(seatIds))
                .allSatisfy(ss -> assertThat(ss.getStatus()).isEqualTo(ShowtimeSeatStatus.BOOKED));
        assertThat(seatHoldRepository.findActiveHeldSeatIdsBySession(session, LocalDateTime.now())).isEmpty();
    }

    @Test
    void concurrentConfirmsSerializeOnTheRowLockWithExactlyOneWinner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    return confirm();
                } catch (ApiException e) {
                    return e;
                }
            }));
        }
        start.countDown();

        List<Object> outcomes = Collections.synchronizedList(new ArrayList<>());
        for (Future<Object> f : results) {
            outcomes.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(outcomes).filteredOn(o -> o instanceof BookingService.Confirmation).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o instanceof ApiException).singleElement()
                .satisfies(o -> assertThat(((ApiException) o).getCode())
                        .isIn(SeatHoldService.SEAT_BOOKED, SeatHoldService.HOLD_EXPIRED));
        assertThat(bookingRepository.countByShowtimeId(showtimeId)).isEqualTo(1);
    }
}
