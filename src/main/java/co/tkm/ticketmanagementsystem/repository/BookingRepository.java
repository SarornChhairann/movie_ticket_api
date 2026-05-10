package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUser(User user);

    long countByUser(User user);

    List<Booking> findByStatus(BookingStatus status);

    List<Booking> findByBookedAtAfter(LocalDateTime since);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE b.status = 'CONFIRMED' AND b.bookedAt >= :since")
    BigDecimal sumRevenueConfirmedSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.bookedAt >= :since")
    long countBookingsSince(@Param("since") LocalDateTime since);

    // Search bookings by booking ID (string match), user email, guest email, or movie title
    @Query("""
        SELECT b FROM Booking b
        LEFT JOIN b.user u
        WHERE (:q IS NULL OR :q = ''
            OR CAST(b.id AS string) LIKE CONCAT('%', :q, '%')
            OR LOWER(COALESCE(u.email,'')) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(COALESCE(u.name,'')) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(COALESCE(b.guestEmail,'')) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(COALESCE(b.guestName,'')) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(b.showtime.movie.title) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY b.bookedAt DESC
        """)
    List<Booking> searchBookings(@Param("q") String q);

    List<Booking> findByUserOrderByBookedAtDesc(User user);

    boolean existsByShowtimeIdAndStatusNot(Long showtimeId, BookingStatus status);

    long countByShowtimeIdAndStatusNot(Long showtimeId, BookingStatus status);

    boolean existsByShowtimeId(Long showtimeId);

    long countByShowtimeId(Long showtimeId);
}
