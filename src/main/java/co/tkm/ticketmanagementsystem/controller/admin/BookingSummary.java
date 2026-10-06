package co.tkm.ticketmanagementsystem.controller.admin;

import co.tkm.ticketmanagementsystem.enums.PaymentStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Shared admin view of a booking row, used by the dashboard, booking list, and user detail endpoints.
 * {@link #from(Booking)} walks lazy relations, so only call it inside a read-only transaction.
 */
public record BookingSummary(Long id, String reference, String movieTitle, String hallName, LocalDate showDate, LocalTime showTime,
                             String customerName, BigDecimal totalAmount, String status, LocalDateTime bookedAt,
                             String paymentStatus) {

    public static BookingSummary from(Booking b) {
        return from(b, null);
    }

    /** [paymentStatus] is null when it was not looked up or the booking has no payment. */
    public static BookingSummary from(Booking b, PaymentStatus paymentStatus) {
        Showtime showtime = b.getShowtime();
        User user = b.getUser();
        return new BookingSummary(b.getId(), b.getReference(), showtime.getMovie().getTitle(),
                showtime.getHall() != null ? showtime.getHall().getName() : null,
                showtime.getShowDate(), showtime.getShowTime(),
                user != null ? user.getName() : b.getGuestName(), b.getTotalAmount(),
                b.getStatus().name(), b.getBookedAt(), paymentStatus == null ? null : paymentStatus.name());
    }
}
