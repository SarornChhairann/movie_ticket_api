package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
