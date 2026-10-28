package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.Ticket;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Booking mail. Nothing is sent while {@code spring.mail.username} is blank (local runs);
 * otherwise a delivery failure is thrown, so the queue listener can retry it. Callers
 * reach this only through {@code BookingMailPublisher}, which keeps it best-effort.
 */
@Slf4j
@Service
public class EmailService {

    /** Edge length of each inline ticket QR, in pixels. */
    static final int QR_SIZE = 250;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final QrCodeService qrCodeService;
    private final String from;

    public EmailService(JavaMailSender mailSender, QrCodeService qrCodeService,
                        @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.qrCodeService = qrCodeService;
        this.from = from;
    }

    /**
     * One HTML message (with a plain-text part) per booking, carrying one inline QR per
     * ticket as a {@code cid:} image: remote images are often blocked, and a phone at the
     * door may have no data. The QR payload is the ticket code, as for the door scan.
     */
    public void sendBookingConfirmation(Booking booking, List<Ticket> tickets) {
        String recipient = resolveRecipient(booking);
        if (recipient == null || !mailConfigured(booking, "confirmation")) return;

        Showtime showtime = booking.getShowtime();
        String movie = showtime.getMovie().getTitle();
        String date = showtime.getShowDate() == null ? "" : showtime.getShowDate().format(DATE);
        String time = showtime.getShowTime() == null ? "" : showtime.getShowTime().format(TIME);
        String hall = showtime.getHall().getName();
        String seats = String.join(", ", tickets.stream().map(t -> t.getSeat().getSeatCode()).toList());
        String total = "$" + booking.getTotalAmount();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart/related inside multipart/alternative: text, HTML, and the inline PNGs.
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject("Your tickets for " + movie + " — " + booking.getReference());
            helper.setText(
                    plainConfirmation(booking, movie, date, time, hall, seats, total, tickets),
                    htmlConfirmation(booking, movie, date, time, hall, seats, total, tickets));
            for (Ticket ticket : tickets) {
                byte[] png = qrCodeService.generateQrPng(ticket.getTicketCode(), QR_SIZE);
                helper.addInline(contentId(ticket), new ByteArrayResource(png), "image/png");
            }
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new MailPreparationException("Could not build the confirmation for " + booking.getReference(), ex);
        }
    }

    public void sendCancellationNotice(Booking booking) {
        String recipient = resolveRecipient(booking);
        if (recipient == null || !mailConfigured(booking, "cancellation")) return;

        String body = "Dear " + resolveName(booking) + ",\n\n" +
                "Your booking " + booking.getReference() + " for \"" +
                booking.getShowtime().getMovie().getTitle() + "\" has been cancelled.\n\n" +
                "If you have any questions please contact our support team.\n\nThank you.";

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(recipient);
        msg.setSubject("Booking Cancelled – " + booking.getReference());
        msg.setText(body);
        mailSender.send(msg);
    }

    private String plainConfirmation(Booking booking, String movie, String date, String time, String hall,
                                     String seats, String total, List<Ticket> tickets) {
        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(resolveName(booking)).append(",\n\n");
        body.append("Your booking is confirmed.\n\n");
        body.append("Movie: ").append(movie).append('\n');
        body.append("Date:  ").append(date).append('\n');
        body.append("Time:  ").append(time).append('\n');
        body.append("Hall:  ").append(hall).append('\n');
        body.append("Seats: ").append(seats).append('\n');
        body.append("Total: ").append(total).append("\n\n");
        body.append("Booking reference: ").append(booking.getReference()).append('\n');
        body.append("(Use it with your phone number to find your tickets in the app.)\n\n");
        body.append("At the entrance, show the QR code for each seat. Each seat has its own code:\n");
        for (Ticket ticket : tickets) {
            body.append("  - Seat ").append(ticket.getSeat().getSeatCode())
                    .append(": ").append(ticket.getTicketCode()).append('\n');
        }
        body.append("\nEnjoy the movie!\n");
        return body.toString();
    }

    private String htmlConfirmation(Booking booking, String movie, String date, String time, String hall,
                                    String seats, String total, List<Ticket> tickets) {
        StringBuilder qrs = new StringBuilder();
        for (Ticket ticket : tickets) {
            qrs.append("""
                    <td style="padding:8px;text-align:center;vertical-align:top">
                      <img src="cid:%s" width="180" height="180" alt="Ticket QR for seat %s" style="display:block;border:0">
                      <div style="font-weight:600;margin-top:6px">Seat %s</div>
                      <div style="color:#666;font-size:12px;font-family:monospace">%s</div>
                    </td>
                    """.formatted(contentId(ticket), esc(ticket.getSeat().getSeatCode()),
                    esc(ticket.getSeat().getSeatCode()), esc(ticket.getTicketCode())));
        }
        return """
                <!doctype html>
                <html><body style="margin:0;padding:24px;background:#f4f4f6;font-family:Arial,Helvetica,sans-serif;color:#1a1a1a">
                  <div style="max-width:560px;margin:0 auto;background:#fff;border-radius:12px;padding:24px">
                    <p style="margin:0 0 12px">Hi %s,</p>
                    <h1 style="font-size:20px;margin:0 0 16px">Your booking is confirmed</h1>
                    <table role="presentation" style="border-collapse:collapse;width:100%%;font-size:14px">
                      <tr><td style="padding:4px 0;color:#666;width:90px">Movie</td><td style="padding:4px 0;font-weight:600">%s</td></tr>
                      <tr><td style="padding:4px 0;color:#666">Date</td><td style="padding:4px 0">%s</td></tr>
                      <tr><td style="padding:4px 0;color:#666">Time</td><td style="padding:4px 0">%s</td></tr>
                      <tr><td style="padding:4px 0;color:#666">Hall</td><td style="padding:4px 0">%s</td></tr>
                      <tr><td style="padding:4px 0;color:#666">Seats</td><td style="padding:4px 0">%s</td></tr>
                      <tr><td style="padding:4px 0;color:#666">Total</td><td style="padding:4px 0">%s</td></tr>
                    </table>
                    <p style="margin:16px 0 4px;font-size:14px">Booking reference</p>
                    <p style="margin:0;font-size:22px;font-weight:700;letter-spacing:2px;font-family:monospace">%s</p>
                    <p style="margin:4px 0 16px;color:#666;font-size:12px">Use it with your phone number to find your tickets in the app.</p>
                    <p style="margin:16px 0 8px;font-size:14px">At the entrance, show the QR code for each seat. Each seat has its own code.</p>
                    <table role="presentation" style="border-collapse:collapse"><tr>%s</tr></table>
                    <p style="margin:24px 0 0;font-size:14px">Enjoy the movie!</p>
                  </div>
                </body></html>
                """.formatted(esc(resolveName(booking)), esc(movie), esc(date), esc(time), esc(hall),
                esc(seats), esc(total), esc(booking.getReference()), qrs);
    }

    private boolean mailConfigured(Booking booking, String kind) {
        if (from == null || from.isBlank()) {
            log.info("Mail not configured (spring.mail.username is blank); no {} sent for {}.",
                    kind, booking.getReference());
            return false;
        }
        return true;
    }

    private static String contentId(Ticket ticket) {
        return "ticket-" + ticket.getId();
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
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
