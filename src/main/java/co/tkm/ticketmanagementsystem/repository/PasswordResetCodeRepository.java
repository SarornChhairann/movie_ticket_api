package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findByUserId(Long userId);
}
