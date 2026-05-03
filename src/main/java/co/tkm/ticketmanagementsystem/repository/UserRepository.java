package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Query("""
        SELECT u FROM User u
        WHERE (:q IS NULL OR :q = ''
            OR LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(COALESCE(u.phone,'')) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY u.id DESC
        """)
    List<User> searchUsers(@Param("q") String q);
}
