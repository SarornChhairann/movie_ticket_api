package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.Booking;

/**
 * The payment provider seam. Swapping the mock for a real gateway means one new
 * implementation of this interface; the booking flow never changes.
 */
public interface PaymentGateway {

    PaymentResult charge(Booking booking, String method);

    record PaymentResult(boolean paid, String reason) {
        public static PaymentResult success() {
            return new PaymentResult(true, null);
        }

        public static PaymentResult failure(String reason) {
            return new PaymentResult(false, reason);
        }
    }
}
