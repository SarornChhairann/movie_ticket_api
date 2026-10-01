package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.enums.Role;
import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.enums.TicketStatus;
import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.HallRepository;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import co.tkm.ticketmanagementsystem.repository.SeatRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import co.tkm.ticketmanagementsystem.repository.TicketRepository;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Sprint 6 against a real (H2) database: customer cancel frees the seats in the same
 * transaction and broadcasts {@code RELEASED} after commit; a cancel inside the window
 * changes nothing; my-bookings is newest first.
 */
@SpringBootTest
class CancelBookingIntegrationTest {

    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private HallRepository hallRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private ShowtimeRepository showtimeRepository;
    @Autowired private ShowtimeSeatRepository showtimeSeatRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private SeatRealtimeService seatRealtimeService;
    @MockitoBean private EmailService emailService;

    private String suffix;
    private User owner;
    private Hall hall;
    private Movie movie;

    @BeforeEach
    void seed() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        owner = userRepository.save(new User(null, "Owner " + suffix, "owner-" + suffix + "@test.com",
                null, "x", Role.CUSTOMER, 0, UserStatus.ACTIVE));

        movie = new Movie();
        movie.setTitle("Cancel IT " + suffix);
        movie.setGenre("Drama");
        movie.setDurationMinutes(120);
        movie.setStatus(MovieStatus.NOW_SHOWING);
        movie.setReleaseDate(LocalDate.now());
        movie = movieRepository.save(movie);

        hall = new Hall();
        hall.setName("IT Hall " + suffix);
        hall.setCity("Phnom Penh");
        hall.setCapacity(2);
        hall = hallRepository.save(hall);
    }

    /** A CONFIRMED booking for [owner] with two BOOKED seats, for a show starting at [start]. */
    private Seeded bookedShow(LocalDateTime start, LocalDateTime bookedAt) {
        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setHall(hall);
        showtime.setShowDate(start.toLocalDate());
        showtime.setShowTime(start.toLocalTime().withSecond(0).withNano(0));
        showtime.setNormalPrice(new BigDecimal("5.00"));
        showtime.setCouplePrice(new BigDecimal("8.00"));
        showtime = showtimeRepository.save(showtime);

        Booking booking = new Booking();
        booking.setUser(owner);
        booking.setShowtime(showtime);
        booking.setTotalAmount(new BigDecimal("12.50"));
        booking.setPaymentMethod("CARD");
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setBookedAt(bookedAt);
        booking = bookingRepository.save(booking);

        List<Long> seatIds = new ArrayList<>();
        for (String code : List.of("E1", "E2")) {
            Seat seat = new Seat();
            seat.setHall(hall);
            seat.setSeatCode(code + "-" + showtime.getId());
            seat.setSeatType(SeatType.NORMAL);
            seat.setRowNumber(5);
            seat = seatRepository.save(seat);

            ShowtimeSeat ss = new ShowtimeSeat();
            ss.setShowtime(showtime);
            ss.setSeat(seat);
            ss.setStatus(ShowtimeSeatStatus.BOOKED);
            seatIds.add(showtimeSeatRepository.save(ss).getId());

            Ticket ticket = new Ticket();
            ticket.setBooking(booking);
            ticket.setSeat(seat);
            ticket.setTicketCode(UUID.randomUUID().toString());
            ticket.setStatus(TicketStatus.ACTIVE);
            ticket.setIssuedAt(bookedAt);
            ticketRepository.save(ticket);
        }
        return new Seeded(booking.getId(), showtime.getId(), seatIds);
    }

    private record Seeded(Long bookingId, Long showtimeId, List<Long> seatIds) {
    }

    @Test
    void ownerCancelOutsideTheWindowFreesSeatsAndPublishesReleaseAfterCommit() {
        Seeded s = bookedShow(LocalDateTime.now().plusDays(3), LocalDateTime.now());

        bookingService.cancelBooking(s.bookingId(), owner.getEmail());

        Booking booking = bookingRepository.findById(s.bookingId()).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(ticketRepository.findByBooking(booking))
                .hasSize(2).allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(TicketStatus.CANCELLED));
        assertThat(showtimeSeatRepository.findAllById(s.seatIds()))
                .allSatisfy(ss -> assertThat(ss.getStatus()).isEqualTo(ShowtimeSeatStatus.AVAILABLE));
        verify(seatRealtimeService).publishReleased(s.showtimeId(), s.seatIds(), null);
    }

    @Test
    void cancelInsideTheWindowIsCancelWindowAndLeavesSeatsBooked() {
        Seeded s = bookedShow(LocalDateTime.now().plusMinutes(90), LocalDateTime.now());

        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.cancelBooking(s.bookingId(), owner.getEmail()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(BookingService.CANCEL_WINDOW);
        assertThat(bookingRepository.findById(s.bookingId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(showtimeSeatRepository.findAllById(s.seatIds()))
                .allSatisfy(ss -> assertThat(ss.getStatus()).isEqualTo(ShowtimeSeatStatus.BOOKED));
        verify(seatRealtimeService, never()).publishReleased(any(), any(), any());
    }

    @Test
    void strangerIsNotOwnerAndNothingChanges() {
        Seeded s = bookedShow(LocalDateTime.now().plusDays(3), LocalDateTime.now());

        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.cancelBooking(s.bookingId(), "stranger-" + suffix + "@test.com"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo(BookingService.NOT_OWNER);
        assertThat(showtimeSeatRepository.findAllById(s.seatIds()))
                .allSatisfy(ss -> assertThat(ss.getStatus()).isEqualTo(ShowtimeSeatStatus.BOOKED));
    }

    @Test
    void myBookingsAreNewestFirst() {
        LocalDateTime now = LocalDateTime.now();
        Seeded oldest = bookedShow(now.plusDays(3), now.minusDays(2));
        Seeded newest = bookedShow(now.plusDays(4), now.minusMinutes(5));
        Seeded middle = bookedShow(now.plusDays(5), now.minusDays(1));

        List<Long> ids = bookingService.getBookingsForUser(owner.getEmail()).stream().map(Booking::getId).toList();

        assertThat(ids).containsExactly(newest.bookingId(), middle.bookingId(), oldest.bookingId());
    }
}
