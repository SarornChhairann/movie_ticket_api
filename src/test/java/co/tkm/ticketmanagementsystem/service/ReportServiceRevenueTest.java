package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.TicketRepository;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceRevenueTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private ReportService reportService;

    private Booking booking(String title, String total, BookingStatus status) {
        Movie movie = new Movie();
        movie.setTitle(title);
        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        Booking b = new Booking();
        b.setShowtime(showtime);
        b.setTotalAmount(new BigDecimal(total));
        b.setStatus(status);
        b.setBookedAt(LocalDate.now().atTime(10, 0));
        return b;
    }

    @Test
    void dailyStatsKeepCentsAndCountOnlyConfirmedRevenue() {
        when(bookingRepository.findByBookedAtAfter(any(LocalDateTime.class))).thenReturn(List.of(
                booking("A", "5.97", BookingStatus.CONFIRMED),
                booking("A", "8.11", BookingStatus.CONFIRMED),
                booking("B", "9.99", BookingStatus.CANCELLED)));

        List<Map<String, Object>> stats = reportService.getDailyStats(3);
        Map<String, Object> today = stats.get(stats.size() - 1);

        assertEquals(LocalDate.now().toString(), today.get("date"));
        assertEquals(3L, today.get("count"));
        assertEquals(new BigDecimal("14.08"), today.get("revenue"));
    }

    @Test
    void topMoviesRevenueKeepsCents() {
        Booking a1 = booking("A", "5.97", BookingStatus.CONFIRMED);
        Booking a2 = booking("A", "8.11", BookingStatus.CONFIRMED);
        when(bookingRepository.findByStatus(BookingStatus.CONFIRMED)).thenReturn(List.of(a1, a2));
        when(ticketRepository.findByBooking(any(Booking.class))).thenReturn(List.of());

        Map<String, Object> top = reportService.getTopMovies(5).get(0);

        assertEquals("A", top.get("title"));
        assertEquals(new BigDecimal("14.08"), top.get("revenue"));
    }
}
