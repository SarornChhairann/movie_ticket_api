package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.TicketRepository;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public BigDecimal getRevenueToday() {
        return bookingRepository.sumRevenueConfirmedSince(LocalDate.now().atStartOfDay());
    }

    @Transactional(readOnly = true)
    public BigDecimal getRevenueThisWeek() {
        return bookingRepository.sumRevenueConfirmedSince(LocalDate.now().minusDays(6).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public BigDecimal getRevenueThisMonth() {
        return bookingRepository.sumRevenueConfirmedSince(LocalDate.now().withDayOfMonth(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public long getBookingsToday() {
        return bookingRepository.countBookingsSince(LocalDate.now().atStartOfDay());
    }

    /**
     * Returns daily booking counts + revenue for the last N days (oldest first).
     * Each entry: {"date": "2026-05-01", "count": 5, "revenue": 45.00}
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDailyStats(int days) {
        LocalDateTime since = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        List<Booking> bookings = bookingRepository.findByBookedAtAfter(since);

        // Per date: booking count and confirmed revenue, summed exactly (no cents dropped)
        Map<String, Long> countByDate = new LinkedHashMap<>();
        Map<String, BigDecimal> revenueByDate = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            String key = LocalDate.now().minusDays(i).toString();
            countByDate.put(key, 0L);
            revenueByDate.put(key, BigDecimal.ZERO);
        }

        for (Booking b : bookings) {
            String key = b.getBookedAt().toLocalDate().toString();
            if (countByDate.containsKey(key)) {
                countByDate.merge(key, 1L, Long::sum);
                if (b.getStatus() == BookingStatus.CONFIRMED && b.getTotalAmount() != null) {
                    revenueByDate.merge(key, b.getTotalAmount(), BigDecimal::add);
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : countByDate.keySet()) {
            result.add(Map.of("date", key, "count", countByDate.get(key), "revenue", revenueByDate.get(key)));
        }
        return result;
    }

    /**
     * Top N movies by confirmed ticket count: [{title, count, revenue}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopMovies(int limit) {
        List<Booking> confirmed = bookingRepository.findByStatus(BookingStatus.CONFIRMED);
        Map<String, Long> ticketsByTitle = new LinkedHashMap<>();
        Map<String, BigDecimal> revenueByTitle = new LinkedHashMap<>();

        for (Booking b : confirmed) {
            String title = b.getShowtime().getMovie().getTitle();
            long ticketCount = ticketRepository.findByBooking(b).size();
            ticketsByTitle.merge(title, ticketCount, Long::sum);
            revenueByTitle.merge(title, b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO, BigDecimal::add);
        }

        return ticketsByTitle.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("title", e.getKey());
                    m.put("tickets", e.getValue());
                    m.put("revenue", revenueByTitle.get(e.getKey()));
                    return m;
                })
                .toList();
    }

    /**
     * Revenue between two dates.
     */
    @Transactional(readOnly = true)
    public BigDecimal getRevenueBetween(LocalDate from, LocalDate to) {
        return bookingRepository.sumRevenueConfirmedSince(from.atStartOfDay());
    }

    /**
     * Generate CSV bytes for bookings matching optional search query.
     */
    @Transactional(readOnly = true)
    public byte[] generateBookingsCsv(String query) {
        List<Booking> bookings = (query == null || query.isBlank())
                ? bookingRepository.findAll()
                : bookingRepository.searchBookings(query);

        StringBuilder sb = new StringBuilder();
        sb.append("ID,Movie,Date,Time,Hall,Customer,Email,Total,Status,PaymentMethod,BookedAt\n");
        for (Booking b : bookings) {
            b.getShowtime().getMovie().getTitle();
            b.getShowtime().getHall().getName();
            String customer = b.getUser() != null ? b.getUser().getName()
                    : (b.getGuestName() != null ? b.getGuestName() : "Guest");
            String email = b.getUser() != null ? b.getUser().getEmail()
                    : (b.getGuestEmail() != null ? b.getGuestEmail() : "");
            sb.append(csv(String.valueOf(b.getId()))).append(",")
              .append(csv(b.getShowtime().getMovie().getTitle())).append(",")
              .append(b.getShowtime().getShowDate()).append(",")
              .append(b.getShowtime().getShowTime()).append(",")
              .append(csv(b.getShowtime().getHall().getName())).append(",")
              .append(csv(customer)).append(",")
              .append(csv(email)).append(",")
              .append(b.getTotalAmount()).append(",")
              .append(b.getStatus()).append(",")
              .append(b.getPaymentMethod() != null ? b.getPaymentMethod() : "").append(",")
              .append(b.getBookedAt()).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
