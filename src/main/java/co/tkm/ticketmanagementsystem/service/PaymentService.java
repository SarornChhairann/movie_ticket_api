package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.PaymentStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Payment;
import co.tkm.ticketmanagementsystem.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Records the outcome of a charge. Booking status belongs to {@link BookingService}. */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment record(Booking booking, String method, PaymentGateway.PaymentResult result) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalAmount());
        payment.setMethod(method);
        payment.setStatus(result.paid() ? PaymentStatus.PAID : PaymentStatus.FAILED);
        payment.setPaidAt(result.paid() ? LocalDateTime.now() : null);
        return paymentRepository.save(payment);
    }

    /** A PayWay payment waiting for the customer: {@code PENDING} until PayWay reports back. */
    @Transactional
    public Payment recordPending(Booking booking, String method, String tranId, String sessionId,
                                 String seatIds, LocalDateTime expiresAt) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalAmount());
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTranId(tranId);
        payment.setSessionId(sessionId);
        payment.setSeatIds(seatIds);
        payment.setExpiresAt(expiresAt);
        payment.setProviderStatus("PENDING");
        return paymentRepository.save(payment);
    }
}
