package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendBookingConfirmation(Booking booking, List<Ticket> tickets) {
        String recipient = resolveRecipient(booking);
        if (recipient == null) return;

        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(resolveName(booking)).append(",\n\n");
        body.append("Your booking is confirmed!\n\n");
        body.append("Movie: ").append(booking.getShowtime().getMovie().getTitle()).append("\n");
        body.append("Date:  ").append(booking.getShowtime().getShowDate()).append("\n");
        body.append("Time:  ").append(booking.getShowtime().getShowTime()).append("\n");
        body.append("Hall:  ").append(booking.getShowtime().getHall().getName()).append("\n");
        body.append("Total: $").append(booking.getTotalAmount()).append("\n\n");
        body.append("Your ticket codes:\n");
        for (Ticket ticket : tickets) {
            body.append("  - ").append(ticket.getTicketCode())
                .append(" (Seat ").append(ticket.getSeat().getSeatCode()).append(")\n");
        }
        body.append("\nThank you for booking with us!\n");

        send(recipient, "Booking Confirmed – #" + booking.getId(), body.toString());
    }

    public void sendCancellationNotice(Booking booking) {
        String recipient = resolveRecipient(booking);
        if (recipient == null) return;

        String body = "Dear " + resolveName(booking) + ",\n\n" +
                "Your booking #" + booking.getId() + " for \"" +
                booking.getShowtime().getMovie().getTitle() + "\" has been cancelled.\n\n" +
                "If you have any questions please contact our support team.\n\nThank you.";

        send(recipient, "Booking Cancelled – #" + booking.getId(), body);
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
        } catch (MailException ex) {
            log.warn("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }

    private String resolveRecipient(Booking booking) {
        if (booking.getUser() != null) return booking.getUser().getEmail();
        if (booking.getGuestEmail() != null && !booking.getGuestEmail().isBlank()) return booking.getGuestEmail();
        return null;
    }

    private String resolveName(Booking booking) {
        if (booking.getUser() != null) return booking.getUser().getName();
        if (booking.getGuestName() != null) return booking.getGuestName();
        return "Guest";
    }
}
