package co.tkm.ticketmanagementsystem.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The booking-mail topology. Messages that still fail after the listener's retries
 * (spring.rabbitmq.listener.simple.retry) are rejected into {@link #MAIL_DLQ}, where
 * they wait for a look in the management UI instead of being lost or retried forever.
 */
@Configuration
public class RabbitMqConfig {

    public static final String MAIL_EXCHANGE = "booking.mail";
    public static final String MAIL_QUEUE = "booking.mail";
    public static final String MAIL_ROUTING_KEY = "booking.mail";
    public static final String MAIL_DLX = "booking.mail.dlx";
    public static final String MAIL_DLQ = "booking.mail.dlq";

    @Bean
    public DirectExchange bookingMailExchange() {
        return new DirectExchange(MAIL_EXCHANGE, true, false);
    }

    @Bean
    public Queue bookingMailQueue() {
        return QueueBuilder.durable(MAIL_QUEUE)
                .deadLetterExchange(MAIL_DLX)
                .deadLetterRoutingKey(MAIL_DLQ)
                .build();
    }

    @Bean
    public Binding bookingMailBinding() {
        return BindingBuilder.bind(bookingMailQueue()).to(bookingMailExchange()).with(MAIL_ROUTING_KEY);
    }

    @Bean
    public DirectExchange bookingMailDeadLetterExchange() {
        return new DirectExchange(MAIL_DLX, true, false);
    }

    @Bean
    public Queue bookingMailDeadLetterQueue() {
        return QueueBuilder.durable(MAIL_DLQ).build();
    }

    @Bean
    public Binding bookingMailDeadLetterBinding() {
        return BindingBuilder.bind(bookingMailDeadLetterQueue()).to(bookingMailDeadLetterExchange()).with(MAIL_DLQ);
    }

    /** JSON on the wire; the auto-configured RabbitTemplate and listener factory pick it up. */
    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter("co.tkm.ticketmanagementsystem.messaging");
    }
}
