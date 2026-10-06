package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.enums.PaymentStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTranId(String tranId);

    Optional<Payment> findByBooking(Booking booking);

    /** {@code [bookingId, status]} rows for [bookingIds]; a booking with no payment has no row. */
    @Query("SELECT p.booking.id, p.status FROM Payment p WHERE p.booking.id IN :bookingIds")
    List<Object[]> findStatusesByBookingIds(@Param("bookingIds") List<Long> bookingIds);

    @Query("SELECT p.booking.id FROM Payment p WHERE p.status = :status")
    List<Long> findBookingIdsByStatus(@Param("status") PaymentStatus status);

    /** Serializes poll, callback, and close on one PayWay payment so completion runs once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.tranId = :tranId")
    Optional<Payment> findByTranIdForUpdate(@Param("tranId") String tranId);

    @Query("SELECT p.tranId FROM Payment p WHERE p.tranId IS NOT NULL AND p.status = :status AND p.sessionId = :sessionId")
    List<String> findTranIdsBySessionAndStatus(@Param("sessionId") String sessionId,
                                               @Param("status") PaymentStatus status);

    @Query("SELECT p.tranId FROM Payment p WHERE p.tranId IS NOT NULL AND p.status = :status AND p.expiresAt < :now")
    List<String> findExpiredTranIds(@Param("status") PaymentStatus status, @Param("now") LocalDateTime now);
}
