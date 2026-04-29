package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByTicketCode(String code);

    List<Ticket> findByBooking(Booking booking);
}
