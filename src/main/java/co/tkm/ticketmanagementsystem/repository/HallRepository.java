package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.Hall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HallRepository extends JpaRepository<Hall, Long> {

    @Query("SELECT DISTINCT h.city FROM Hall h WHERE h.city IS NOT NULL AND h.city <> '' ORDER BY h.city")
    List<String> findDistinctCities();

    List<Hall> findByCityIgnoreCase(String city);

    @Query("SELECT COUNT(h) FROM Hall h WHERE h.cinema.id = :cinemaId")
    long countByCinemaId(@Param("cinemaId") UUID cinemaId);

    Optional<Hall> findByName(String name);

    /** Customer hall list: cinema fetched in the same query, ordered by cinema name then hall name. */
    @Query("SELECT h FROM Hall h LEFT JOIN FETCH h.cinema c ORDER BY c.name ASC, h.name ASC")
    List<Hall> findAllWithCinemaOrdered();

    @Query("SELECT h FROM Hall h LEFT JOIN FETCH h.cinema WHERE h.id = :id")
    Optional<Hall> findByIdWithCinema(@Param("id") Long id);
}
