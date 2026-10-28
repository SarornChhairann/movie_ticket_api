package co.tkm.ticketmanagementsystem.messaging;

import co.tkm.ticketmanagementsystem.config.RabbitMqConfig;
import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.TicketRepository;
import co.tkm.ticketmanagementsystem.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailSendException;

import java.net.ConnectException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingMailPublisherTest {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final BookingMailHandler handler = mock(BookingMailHandler.class);

    private BookingMailPublisher publisher(boolean queueEnabled) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("rabbitTemplate", rabbit);
        return new BookingMailPublisher(beans.getBeanProvider(RabbitTemplate.class), handler, queueEnabled);
    }

    @Test
    void withTheQueueOnTheMailIsQueuedNotSent() {
        publisher(true).confirmed(7L);

        verify(rabbit).convertAndSend(RabbitMqConfig.MAIL_EXCHANGE, RabbitMqConfig.MAIL_ROUTING_KEY,
                new BookingMailMessage(7L, BookingMailMessage.Kind.CONFIRMED));
        verify(handler, never()).handle(any());
    }

    @Test
    void aBrokerThatCannotBeReachedFallsBackToSendingDirectly() {
        doThrow(new AmqpConnectException(new ConnectException("refused")))
                .when(rabbit).convertAndSend(any(String.class), any(String.class), any(Object.class));

        publisher(true).cancelled(7L);

        verify(handler).handle(new BookingMailMessage(7L, BookingMailMessage.Kind.CANCELLED));
    }

    @Test
    void withTheQueueOffTheMailIsSentDirectlyAndAFailureNeverReachesTheCaller() {
        doThrow(new MailSendException("SMTP down")).when(handler).handle(any());

        assertThatCode(() -> publisher(false).confirmed(7L)).doesNotThrowAnyException();
        verify(rabbit, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void theHandlerSkipsAConfirmationForABookingCancelledWhileQueuedAndRethrowsMailErrors() {
        BookingRepository bookings = mock(BookingRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        EmailService email = mock(EmailService.class);
        BookingMailHandler real = new BookingMailHandler(bookings, tickets, email);
        Booking cancelled = new Booking();
        cancelled.setStatus(BookingStatus.CANCELLED);
        Booking confirmed = new Booking();
        confirmed.setStatus(BookingStatus.CONFIRMED);
        when(bookings.findById(1L)).thenReturn(Optional.of(cancelled));
        when(bookings.findById(2L)).thenReturn(Optional.of(confirmed));
        when(tickets.findByBooking(confirmed)).thenReturn(List.of());
        doThrow(new MailSendException("SMTP down")).when(email).sendBookingConfirmation(eq(confirmed), any());

        real.handle(new BookingMailMessage(1L, BookingMailMessage.Kind.CONFIRMED));
        verify(email, never()).sendBookingConfirmation(eq(cancelled), any());

        // Rethrown so the listener container retries, then dead-letters.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> real.handle(new BookingMailMessage(2L, BookingMailMessage.Kind.CONFIRMED)))
                .isInstanceOf(MailSendException.class);
    }
}
