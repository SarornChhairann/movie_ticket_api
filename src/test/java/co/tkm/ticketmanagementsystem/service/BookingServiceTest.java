package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.dto.CheckoutData;
import co.tkm.ticketmanagementsystem.enums.Role;
import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.User;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ShowtimeRepository showtimeRepository;
    @Mock
    private ShowtimeSeatRepository showtimeSeatRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentService paymentService;
    @Mock
    private TicketService ticketService;

    @InjectMocks
    private BookingService bookingService;

    private User user;
    private Showtime showtime;

    @BeforeEach
    void setUp() {
        user = new User(1L, "Test User", "user@test.com", "x", Role.CUSTOMER);

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

        CheckoutData checkout = bookingService.buildCheckout(10L, List.of(100L, 101L, 102L), "user@test.com");

        assertEquals(new BigDecimal("13.00"), checkout.getTotalAmount());
    }

    @Test
    void buildCheckout_rejectsOddCoupleSeatSelection() {
        ShowtimeSeat coupleSeat = seat(200L, SeatType.COUPLE, ShowtimeSeatStatus.AVAILABLE, "A1");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(showtimeRepository.findById(10L)).thenReturn(Optional.of(showtime));
        when(showtimeSeatRepository.findAllById(List.of(200L))).thenReturn(List.of(coupleSeat));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bookingService.buildCheckout(10L, List.of(200L), "user@test.com"));
        assertEquals("Couple seats must be selected in pairs.", ex.getMessage());
    }

    @Test
    void buildCheckout_rejectsInvalidCouplePairCombination() {
        ShowtimeSeat coupleSeatA1 = seat(210L, SeatType.COUPLE, ShowtimeSeatStatus.AVAILABLE, "A1");
        ShowtimeSeat coupleSeatA3 = seat(211L, SeatType.COUPLE, ShowtimeSeatStatus.AVAILABLE, "A3");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(showtimeRepository.findById(10L)).thenReturn(Optional.of(showtime));
        when(showtimeSeatRepository.findAllById(List.of(210L, 211L))).thenReturn(List.of(coupleSeatA1, coupleSeatA3));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bookingService.buildCheckout(10L, List.of(210L, 211L), "user@test.com"));
        assertEquals("Couple seats must be selected in pairs.", ex.getMessage());
    }

    @Test
    void buildCheckout_rejectsBookedSeat() {
        ShowtimeSeat bookedSeat = seat(300L, SeatType.NORMAL, ShowtimeSeatStatus.BOOKED, "C3");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(showtimeRepository.findById(10L)).thenReturn(Optional.of(showtime));
        when(showtimeSeatRepository.findAllById(List.of(300L))).thenReturn(List.of(bookedSeat));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bookingService.buildCheckout(10L, List.of(300L), "user@test.com"));
        assertEquals("Some seats were already booked. Please reselect seats.", ex.getMessage());
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
