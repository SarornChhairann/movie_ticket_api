package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    List<Showtime> findByMovie(Movie movie);

    Optional<Showtime> findById(Long id);

    List<Showtime> findByShowDateBetweenOrderByShowDateAscShowTimeAsc(LocalDate from, LocalDate to);

    List<Showtime> findByShowDate(LocalDate date);

    @Query("""
        SELECT s FROM Showtime s
        JOIN FETCH s.movie
        WHERE s.hall.id = :hallId AND s.showDate = :showDate
        """)
    List<Showtime> findByHallIdAndShowDateWithMovie(@Param("hallId") Long hallId,
                                                    @Param("showDate") LocalDate showDate);
}
