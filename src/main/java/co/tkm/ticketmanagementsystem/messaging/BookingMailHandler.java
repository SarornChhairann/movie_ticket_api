package co.tkm.ticketmanagementsystem.messaging;

import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.TicketRepository;
import co.tkm.ticketmanagementsystem.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends one booking mail. Runs in its own read-only transaction so the lazy showtime,
 * movie, hall, and seats can be walked, both from the queue listener and from the
 * direct fallback inside an after-commit callback. A mail failure is rethrown so the
 * listener can retry it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingMailHandler {

    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final EmailService emailService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void handle(BookingMailMessage message) {
        Booking booking = bookingRepository.findById(message.bookingId()).orElse(null);
        if (booking == null) {
            log.warn("Booking {} is gone; no {} mail sent.", message.bookingId(), message.kind());
            return;
        }
        switch (message.kind()) {
            case CONFIRMED -> {
                // Cancelled while the message waited: the cancellation mail says what matters.
                if (booking.getStatus() != BookingStatus.CONFIRMED) {
                    log.info("Booking {} is {}; confirmation mail skipped.", booking.getId(), booking.getStatus());
                    return;
                }
                emailService.sendBookingConfirmation(booking, ticketRepository.findByBooking(booking));
            }
            case CANCELLED -> emailService.sendCancellationNotice(booking);
        }
    }
}
