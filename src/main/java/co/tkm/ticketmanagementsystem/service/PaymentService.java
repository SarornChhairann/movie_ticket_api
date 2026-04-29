package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.enums.PaymentStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Payment;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public Payment processMockPayment(Booking booking) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalAmount());
        payment.setMethod("MOCK_CARD");
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        Payment savedPayment = paymentRepository.save(payment);

        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);
        return savedPayment;
    }
}
