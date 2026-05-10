package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    List<Movie> findByStatus(MovieStatus status);

    @Query("SELECT m FROM Movie m WHERE " +
           "(:status IS NULL OR m.status = :status) AND " +
           "(:genre = '' OR LOWER(m.genre) LIKE LOWER(CONCAT('%', :genre, '%'))) AND " +
           "(:language = '' OR LOWER(m.language) LIKE LOWER(CONCAT('%', :language, '%'))) AND " +
           "(:format IS NULL OR m.format = :format) AND " +
           "(:rating = '' OR m.rating = :rating) AND " +
           "(:search = '' OR LOWER(m.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY m.title")
    List<Movie> findFiltered(@Param("status") MovieStatus status,
                             @Param("genre") String genre,
                             @Param("language") String language,
                             @Param("format") String format,
                             @Param("rating") String rating,
                             @Param("search") String search);

    @Query("SELECT DISTINCT m.genre FROM Movie m WHERE m.genre IS NOT NULL AND m.genre <> '' ORDER BY m.genre")
    List<String> findDistinctGenres();

    @Query("SELECT DISTINCT m.language FROM Movie m WHERE m.language IS NOT NULL AND m.language <> '' ORDER BY m.language")
    List<String> findDistinctLanguages();

    java.util.Optional<Movie> findFirstByStatusAndImagePathIsNotNullOrderByIdDesc(MovieStatus status);

    List<Movie> findTop6ByStatusOrderByIdDesc(MovieStatus status);

    @Query("""
        SELECT m FROM Movie m
        WHERE m.status = co.tkm.ticketmanagementsystem.enums.MovieStatus.COMING_SOON
          AND m.releaseDate IS NOT NULL
          AND m.releaseDate >= :startDate
          AND m.releaseDate <= :endDate
        ORDER BY m.releaseDate ASC, m.title ASC
        """)
    List<Movie> findComingSoonBetween(@Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);
}
