package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.Booking;
import org.springframework.stereotype.Component;

/** Dev provider: every charge succeeds. Replace with a real gateway bean in production. */
@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(Booking booking, String method) {
        return PaymentResult.success();
    }
}
