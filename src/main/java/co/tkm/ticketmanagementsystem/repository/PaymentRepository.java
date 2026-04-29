package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
