package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.User;
import org.springframework.http.HttpStatus;

/**
 * Who may read a booking (analytics invariant 9): the signed-in user it belongs to, or
 * the {@code X-Session-Id} that created it. Everyone else is told it does not exist,
 * so sequential booking ids cannot be probed for names, phones, or ticket codes.
 */
public final class BookingAccess {

    public static final String BOOKING_NOT_FOUND = "BOOKING_NOT_FOUND";

    private BookingAccess() {
    }

    public static boolean canRead(Booking booking, String userEmail, String sessionId) {
        if (booking == null) return false;
        User owner = booking.getUser();
        if (owner != null && userEmail != null && owner.getEmail() != null
                && owner.getEmail().equalsIgnoreCase(userEmail)) {
            return true;
        }
        return booking.getSessionId() != null && sessionId != null
                && booking.getSessionId().equals(sessionId);
    }

    public static void assertCanRead(Booking booking, String userEmail, String sessionId) {
        if (!canRead(booking, userEmail, sessionId)) {
            throw notFound();
        }
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, BOOKING_NOT_FOUND, "Booking not found.");
    }
}
