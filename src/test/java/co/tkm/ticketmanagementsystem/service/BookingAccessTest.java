package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingAccessTest {

    private static final String SESSION = "3f2b8c1e-6a4d-4c3b-9e2f-1a2b3c4d5e6f";
    private static final String OTHER_SESSION = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";

    private static Booking booking(String ownerEmail, String sessionId) {
        Booking booking = new Booking();
        if (ownerEmail != null) {
            User user = new User();
            user.setEmail(ownerEmail);
            booking.setUser(user);
        }
        booking.setSessionId(sessionId);
        return booking;
    }

    @Test
    void theOwningUserReadsFromAnySession() {
        assertThat(BookingAccess.canRead(booking("me@test.com", SESSION), "ME@test.com", OTHER_SESSION)).isTrue();
    }

    @Test
    void theCreatingSessionReadsAGuestBooking() {
        assertThat(BookingAccess.canRead(booking(null, SESSION), null, SESSION)).isTrue();
    }

    @Test
    void theCreatingSessionAlsoReadsAUserBooking() {
        assertThat(BookingAccess.canRead(booking("me@test.com", SESSION), null, SESSION)).isTrue();
    }

    @Test
    void aStrangerCannotRead() {
        assertThat(BookingAccess.canRead(booking("me@test.com", SESSION), "you@test.com", OTHER_SESSION)).isFalse();
        assertThat(BookingAccess.canRead(booking(null, SESSION), "you@test.com", OTHER_SESSION)).isFalse();
    }

    @Test
    void aGuestBookingWithNoStoredSessionIsReadByNobody() {
        assertThat(BookingAccess.canRead(booking(null, null), null, null)).isFalse();
        assertThat(BookingAccess.canRead(booking(null, null), null, SESSION)).isFalse();
    }

    @Test
    void refusalIsA404BookingNotFound() {
        ApiException ex = assertThrows(ApiException.class,
                () -> BookingAccess.assertCanRead(booking(null, SESSION), null, OTHER_SESSION));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo(BookingAccess.BOOKING_NOT_FOUND);
    }
}
