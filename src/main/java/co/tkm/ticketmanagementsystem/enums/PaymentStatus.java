package co.tkm.ticketmanagementsystem.enums;

public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    /** A paid booking was cancelled; the money is owed back and an admin refunds it by hand. */
    REFUND_PENDING,
    /** An admin returned the money of a {@link #REFUND_PENDING} payment. */
    REFUNDED
}
