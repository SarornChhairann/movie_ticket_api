package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The provider declined the charge. Thrown after the cancelled booking and failed
 * payment are written, and excluded from rollback so that audit trail survives.
 */
public class PaymentFailedException extends ApiException {

    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";

    public PaymentFailedException(String reason) {
        super(HttpStatus.PAYMENT_REQUIRED, PAYMENT_FAILED,
                reason == null || reason.isBlank() ? "Payment failed. Please try again." : reason);
    }
}
