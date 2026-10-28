package co.tkm.ticketmanagementsystem.messaging;

import co.tkm.ticketmanagementsystem.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes the booking-mail queue. A thrown mail error is retried by the listener
 * container (spring.rabbitmq.listener.simple.retry) and then dead-lettered.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mail.queue.enabled", havingValue = "true")
public class BookingMailListener {

    private final BookingMailHandler handler;

    @RabbitListener(queues = RabbitMqConfig.MAIL_QUEUE)
    public void onMessage(BookingMailMessage message) {
        handler.handle(message);
    }
}
