package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.TicketStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import co.tkm.ticketmanagementsystem.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceCheckInTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private ShowtimeSeatRepository showtimeSeatRepository;
    @InjectMocks private TicketService ticketService;

    private Ticket ticketForToday(TicketStatus status) {
        Movie movie = new Movie();
        movie.setTitle("Test Movie");
        Hall hall = new Hall();
        hall.setName("Hall 1");
        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setHall(hall);
        showtime.setShowDate(LocalDate.now());
        Booking booking = new Booking();
        booking.setShowtime(showtime);
        Seat seat = new Seat();
        seat.setSeatCode("A1");

        Ticket ticket = new Ticket();
        ticket.setTicketCode("TKT-1");
        ticket.setBooking(booking);
        ticket.setSeat(seat);
        ticket.setStatus(status);
        return ticket;
    }

    @Test
    void checkInMarksActiveTicketUsedAndStampsScannedAt() {
        Ticket ticket = ticketForToday(TicketStatus.ACTIVE);
        when(ticketRepository.findByTicketCode("TKT-1")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = ticketService.checkIn("TKT-1");

        assertEquals(TicketStatus.USED, result.getStatus());
        assertNotNull(result.getScannedAt());
    }

    @Test
    void secondScanIsRejectedAndLeavesTicketUnchanged() {
        Ticket ticket = ticketForToday(TicketStatus.USED);
        LocalDateTime firstScan = LocalDateTime.now().minusMinutes(5);
        ticket.setScannedAt(firstScan);
        when(ticketRepository.findByTicketCode("TKT-1")).thenReturn(Optional.of(ticket));

        assertThrows(IllegalArgumentException.class, () -> ticketService.checkIn("TKT-1"));

        assertEquals(TicketStatus.USED, ticket.getStatus());
        assertEquals(firstScan, ticket.getScannedAt());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void cancelledTicketIsRejected() {
        Ticket ticket = ticketForToday(TicketStatus.CANCELLED);
        when(ticketRepository.findByTicketCode("TKT-1")).thenReturn(Optional.of(ticket));

        assertThrows(IllegalArgumentException.class, () -> ticketService.checkIn("TKT-1"));

        assertEquals(TicketStatus.CANCELLED, ticket.getStatus());
        assertNull(ticket.getScannedAt());
        verify(ticketRepository, never()).save(any());
    }
}
