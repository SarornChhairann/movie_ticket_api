package co.tkm.ticketmanagementsystem.controller.admin;

import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.service.BookingService;
import co.tkm.ticketmanagementsystem.service.HallService;
import co.tkm.ticketmanagementsystem.service.MovieService;
import co.tkm.ticketmanagementsystem.service.ReportService;
import co.tkm.ticketmanagementsystem.service.ShowtimeService;
import co.tkm.ticketmanagementsystem.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin: Dashboard", description = "Back-office overview: counts, revenue, top movies, recent bookings")
public class AdminDashboardController {

    private final MovieService movieService;
    private final HallService hallService;
    private final ShowtimeService showtimeService;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final UserService userService;
    private final ReportService reportService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get dashboard stats: counts, revenue, top movies, daily stats, recent bookings")
    @Transactional(readOnly = true)
    public ResponseEntity<DashboardResponse> dashboard() {
        List<BookingSummary> recentBookings = bookingService.getAllBookingsForAdmin().stream()
                .limit(5)
                .map(BookingSummary::from)
                .toList();

        return ResponseEntity.ok(new DashboardResponse(
                movieService.count(), hallService.count(), showtimeService.count(), bookingRepository.count(),
                (long) userService.findAll().size(),
                reportService.getRevenueToday(), reportService.getRevenueThisWeek(), reportService.getRevenueThisMonth(),
                reportService.getBookingsToday(), reportService.getTopMovies(5),
                reportService.getDailyStats(14), recentBookings));
    }

    public record DashboardResponse(long movieCount, long hallCount, long showtimeCount, long bookingCount,
                                     long userCount, BigDecimal revenueToday, BigDecimal revenueWeek,
                                     BigDecimal revenueMonth, long bookingsToday, List<Map<String, Object>> topMovies,
                                     List<Map<String, Object>> dailyStats, List<BookingSummary> recentBookings) {
    }
}
