package co.tkm.ticketmanagementsystem.messaging;

/**
 * What goes on the booking-mail queue: only the booking id and which mail to send.
 * The consumer reloads the booking, so a message never carries a stale or lazy entity.
 */
public record BookingMailMessage(Long bookingId, Kind kind) {

    public enum Kind { CONFIRMED, CANCELLED }
}
