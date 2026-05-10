package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    List<Showtime> findByMovie(Movie movie);

    Optional<Showtime> findById(Long id);

    @Query("""
        SELECT s FROM Showtime s
        JOIN FETCH s.movie m
        JOIN FETCH s.hall h
        LEFT JOIN FETCH h.cinema c
        WHERE m.id = :movieId
        ORDER BY s.showDate ASC, s.showTime ASC
        """)
    List<Showtime> findByMovieIdWithHall(@Param("movieId") Long movieId);

    List<Showtime> findByShowDateBetweenOrderByShowDateAscShowTimeAsc(LocalDate from, LocalDate to);

    List<Showtime> findByShowDate(LocalDate date);

    @Query("""
        SELECT s FROM Showtime s
        JOIN FETCH s.movie
        WHERE s.hall.id = :hallId AND s.showDate = :showDate
        """)
    List<Showtime> findByHallIdAndShowDateWithMovie(@Param("hallId") Long hallId,
                                                    @Param("showDate") LocalDate showDate);

    @Query("""
        SELECT s FROM Showtime s
        JOIN FETCH s.movie m
        JOIN FETCH s.hall h
        WHERE m.id = :movieId
          AND s.showDate = :showDate
          AND (:city = '' OR LOWER(h.city) = LOWER(:city))
        ORDER BY s.showTime ASC
        """)
    List<Showtime> findForMovieDateCity(@Param("movieId") Long movieId,
                                        @Param("showDate") LocalDate showDate,
                                        @Param("city") String city);

    @Query("""
        SELECT s FROM Showtime s
        JOIN FETCH s.movie m
        JOIN FETCH s.hall h
        LEFT JOIN FETCH h.cinema c
        WHERE m.id = :movieId
          AND s.showDate = :showDate
          AND (:cinemaId IS NULL OR c.id = :cinemaId)
        ORDER BY s.showTime ASC
        """)
    List<Showtime> findForMovieDateCinema(@Param("movieId") Long movieId,
                                          @Param("showDate") LocalDate showDate,
                                          @Param("cinemaId") UUID cinemaId);

    @Query("""
        SELECT DISTINCT m.id FROM Showtime s
        JOIN s.movie m
        JOIN s.hall h
        WHERE s.showDate = :showDate
          AND s.blocked = false
          AND (:city = '' OR LOWER(h.city) = LOWER(:city))
        """)
    List<Long> findAvailableMovieIds(@Param("showDate") LocalDate showDate,
                                     @Param("city") String city);

    @Query("""
        SELECT DISTINCT m.id FROM Showtime s
        JOIN s.movie m
        JOIN s.hall h
        WHERE s.showDate = :showDate
          AND s.blocked = false
          AND (:cinemaId IS NULL OR h.cinema.id = :cinemaId)
        """)
    List<Long> findAvailableMovieIdsByCinema(@Param("showDate") LocalDate showDate,
                                             @Param("cinemaId") UUID cinemaId);

    @Query("""
        SELECT s FROM Showtime s
        JOIN FETCH s.movie m
        JOIN FETCH s.hall h
        WHERE s.showDate = :showDate
          AND s.blocked = false
          AND (:city = '' OR LOWER(h.city) = LOWER(:city))
        ORDER BY m.title ASC, s.showTime ASC
        """)
    List<Showtime> findByShowDateCityNotBlocked(@Param("showDate") LocalDate showDate,
                                                @Param("city") String city);

    @Query("""
        SELECT s FROM Showtime s
        JOIN FETCH s.movie m
        JOIN FETCH s.hall h
        LEFT JOIN FETCH h.cinema c
        WHERE s.showDate = :showDate
          AND s.blocked = false
          AND (:cinemaId IS NULL OR c.id = :cinemaId)
        ORDER BY m.title ASC, s.showTime ASC
        """)
    List<Showtime> findByShowDateCinemaNotBlocked(@Param("showDate") LocalDate showDate,
                                                  @Param("cinemaId") UUID cinemaId);
}
