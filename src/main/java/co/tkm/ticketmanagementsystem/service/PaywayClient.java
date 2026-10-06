package co.tkm.ticketmanagementsystem.service;

import java.util.Map;

/**
 * The three PayWay checkout calls this app makes. {@link HttpPaywayClient} talks to the
 * sandbox; tests mock this interface. Every method throws {@link PaywayException} when
 * PayWay is not configured, unreachable, or answers with an error.
 */
public interface PaywayClient {

    /** Purchase with the given form fields (hash added by the client). */
    PurchaseResult purchase(Map<String, String> fields);

    /** Check transaction: PayWay's {@code data.payment_status} (APPROVED, PENDING, DECLINED, ...). */
    String checkTransaction(String tranId);

    /** Close transaction: true when PayWay accepted the close. */
    boolean closeTransaction(String tranId);

    /** True when the sandbox keys are set. */
    boolean isConfigured();

    /** {@code checkout_qr_url} and {@code abapay_deeplink} from the Purchase response. */
    record PurchaseResult(String checkoutUrl, String abaDeeplink) {
    }

    class PaywayException extends RuntimeException {
        public PaywayException(String message) {
            super(message);
        }

        public PaywayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
