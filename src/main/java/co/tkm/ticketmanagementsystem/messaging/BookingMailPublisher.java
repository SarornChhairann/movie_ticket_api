package co.tkm.ticketmanagementsystem.messaging;

import co.tkm.ticketmanagementsystem.config.RabbitMqConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hands booking mail to RabbitMQ so a slow or broken SMTP server never holds up a
 * checkout or a cancel. Callers publish after their transaction commits. With the
 * queue switched off ({@code app.mail.queue.enabled=false}), or when the broker cannot
 * be reached, the mail is sent right here instead. Either way it stays best-effort:
 * nothing thrown from here reaches the booking.
 */
@Slf4j
@Component
public class BookingMailPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final BookingMailHandler handler;
    private final boolean queueEnabled;

    public BookingMailPublisher(ObjectProvider<RabbitTemplate> rabbitTemplate,
                                BookingMailHandler handler,
                                @Value("${app.mail.queue.enabled:false}") boolean queueEnabled) {
        this.rabbitTemplate = rabbitTemplate.getIfAvailable();
        this.handler = handler;
        this.queueEnabled = queueEnabled && this.rabbitTemplate != null;
    }

    public void confirmed(Long bookingId) {
        dispatch(new BookingMailMessage(bookingId, BookingMailMessage.Kind.CONFIRMED));
    }

    public void cancelled(Long bookingId) {
        dispatch(new BookingMailMessage(bookingId, BookingMailMessage.Kind.CANCELLED));
    }

    private void dispatch(BookingMailMessage message) {
        if (queueEnabled) {
            try {
                rabbitTemplate.convertAndSend(RabbitMqConfig.MAIL_EXCHANGE, RabbitMqConfig.MAIL_ROUTING_KEY, message);
                return;
            } catch (AmqpException ex) {
                log.warn("Mail queue unavailable ({}); sending booking {} {} mail directly.",
                        ex.getMessage(), message.bookingId(), message.kind());
            }
        }
        try {
            handler.handle(message);
        } catch (RuntimeException ex) {
            log.warn("Booking {} {} mail failed: {}", message.bookingId(), message.kind(), ex.getMessage());
        }
    }
}
