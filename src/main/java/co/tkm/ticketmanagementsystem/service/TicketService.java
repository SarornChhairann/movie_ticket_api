package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.enums.TicketStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import co.tkm.ticketmanagementsystem.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;

    @Transactional
    public List<Ticket> issueTickets(Booking booking, List<ShowtimeSeat> selectedShowtimeSeats) {
        List<Ticket> tickets = new ArrayList<>();
        LocalDateTime issuedAt = LocalDateTime.now();

        for (ShowtimeSeat showtimeSeat : selectedShowtimeSeats) {
            showtimeSeat.setStatus(ShowtimeSeatStatus.BOOKED);

            Ticket ticket = new Ticket();
            ticket.setBooking(booking);
            ticket.setSeat(showtimeSeat.getSeat());
            ticket.setTicketCode(UUID.randomUUID().toString());
            ticket.setStatus(TicketStatus.ACTIVE);
            ticket.setIssuedAt(issuedAt);
            tickets.add(ticket);
        }

        showtimeSeatRepository.saveAll(selectedShowtimeSeats);
        return ticketRepository.saveAll(tickets);
    }

    @Transactional(readOnly = true)
    public Ticket findByTicketCode(String ticketCode) {
        Ticket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketCode));
        ticket.getBooking().getShowtime().getMovie().getTitle();
        ticket.getBooking().getShowtime().getHall().getName();
        if (ticket.getBooking().getUser() != null) {
            ticket.getBooking().getUser().getName();
        }
        ticket.getSeat().getSeatCode();
        ticket.getSeat().getSeatType();
        return ticket;
    }

    @Transactional(readOnly = true)
    public List<Ticket> findByBooking(Booking booking) {
        List<Ticket> tickets = ticketRepository.findByBooking(booking);
        tickets.forEach(ticket -> ticket.getSeat().getSeatCode());
        return tickets;
    }

    @Transactional
    public Ticket checkIn(String ticketCode) {
        Ticket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketCode));

        ticket.getBooking().getShowtime().getMovie().getTitle();
        ticket.getBooking().getShowtime().getHall().getName();
        ticket.getSeat().getSeatCode();

        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new IllegalArgumentException("This ticket has been cancelled.");
        }
        if (ticket.getStatus() == TicketStatus.USED || ticket.getScannedAt() != null) {
            throw new IllegalArgumentException("Ticket already scanned at " + ticket.getScannedAt() + ".");
        }

        LocalDate showDate = ticket.getBooking().getShowtime().getShowDate();
        if (!showDate.equals(LocalDate.now())) {
            throw new IllegalArgumentException("Ticket is valid for " + showDate + ", not today.");
        }

        ticket.setStatus(TicketStatus.USED);
        ticket.setScannedAt(LocalDateTime.now());
        return ticketRepository.save(ticket);
    }
}
