package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {
    List<Showtime> findByMovie(Movie movie);

    Optional<Showtime> findById(Long id);
}
