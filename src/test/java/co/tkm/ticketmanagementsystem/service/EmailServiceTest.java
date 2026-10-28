package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.Ticket;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private EmailService service(String username) {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        return new EmailService(mailSender, new QrCodeService(), username);
    }

    @Test
    void confirmationIsHtmlFromTheMailAccountWithOneInlineQrPerTicket() throws Exception {
        Booking booking = guestBooking();
        List<Ticket> tickets = List.of(ticket(1L, "TKT-AAA", "F6"), ticket(2L, "TKT-BBB", "F7"));

        service("cinema@example.com").sendBookingConfirmation(booking, tickets);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        message.saveChanges();
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("cinema@example.com");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("dara@example.com");
        assertThat(message.getSubject()).isEqualTo("Your tickets for Dune <2> — AB3DEF7H");

        List<Part> parts = new ArrayList<>();
        collect(message, parts);
        List<Part> pngs = parts.stream().filter(p -> isType(p, "image/png")).toList();
        assertThat(pngs).hasSize(2);
        assertThat(pngs).allSatisfy(p -> assertThat(p.getDisposition()).isEqualTo(Part.INLINE));
        assertThat(pngs.stream().map(p -> header(p, "Content-ID")).toList())
                .containsExactly("<ticket-1>", "<ticket-2>");

        String html = parts.stream().filter(p -> isType(p, "text/html")).findFirst().orElseThrow().getContent().toString();
        assertThat(html).contains("cid:ticket-1", "cid:ticket-2", "Seat F6", "AB3DEF7H", "Dune &lt;2&gt;");
        String text = parts.stream().filter(p -> isType(p, "text/plain")).findFirst().orElseThrow().getContent().toString();
        assertThat(text).contains("Seat F6: TKT-AAA", "Booking reference: AB3DEF7H");
    }

    @Test
    void nothingIsSentWhileTheMailUsernameIsBlank() {
        service(" ").sendBookingConfirmation(guestBooking(), List.of(ticket(1L, "TKT-AAA", "F6")));
        service("").sendCancellationNotice(guestBooking());

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(mailSender, never()).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    private static Booking guestBooking() {
        Movie movie = new Movie();
        movie.setTitle("Dune <2>");
        Hall hall = new Hall();
        hall.setName("Hall 1");
        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setHall(hall);
        showtime.setShowDate(LocalDate.of(2026, 10, 2));
        showtime.setShowTime(LocalTime.of(19, 30));
        Booking booking = new Booking();
        booking.setShowtime(showtime);
        booking.setGuestName("Dara");
        booking.setGuestEmail("dara@example.com");
        booking.setTotalAmount(new BigDecimal("14.50"));
        booking.setReference("AB3DEF7H");
        return booking;
    }

    private static Ticket ticket(Long id, String code, String seatCode) {
        Seat seat = new Seat();
        seat.setSeatCode(seatCode);
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setTicketCode(code);
        ticket.setSeat(seat);
        return ticket;
    }

    private static void collect(Part part, List<Part> out) throws Exception {
        out.add(part);
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                collect(child, out);
            }
        }
    }

    private static boolean isType(Part part, String type) {
        try {
            return part.isMimeType(type);
        } catch (Exception e) {
            return false;
        }
    }

    private static String header(Part part, String name) {
        try {
            return part.getHeader(name)[0];
        } catch (Exception e) {
            return null;
        }
    }
}
