package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Gives every booking made before sprint 13 a reference (G29). New bookings get one on insert. */
@Component
@RequiredArgsConstructor
public class BookingReferenceBackfill {

    private static final Logger log = LoggerFactory.getLogger(BookingReferenceBackfill.class);

    private final BookingRepository bookingRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        List<Booking> missing = bookingRepository.findByReferenceIsNull();
        if (missing.isEmpty()) return;
        missing.forEach(b -> b.setReference(BookingReference.generate()));
        bookingRepository.saveAll(missing);
        log.info("Gave {} existing bookings a reference", missing.size());
    }
}
