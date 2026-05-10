package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.MovieReminder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieReminderRepository extends JpaRepository<MovieReminder, Long> {
    boolean existsByMovie_IdAndEmailIgnoreCase(Long movieId, String email);
}

