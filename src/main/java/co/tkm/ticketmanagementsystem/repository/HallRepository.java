package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.Hall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HallRepository extends JpaRepository<Hall, Long> {

    @Query("SELECT DISTINCT h.city FROM Hall h WHERE h.city IS NOT NULL AND h.city <> '' ORDER BY h.city")
    List<String> findDistinctCities();

    List<Hall> findByCityIgnoreCase(String city);

    @Query("SELECT COUNT(h) FROM Hall h WHERE h.cinema.id = :cinemaId")
    long countByCinemaId(@Param("cinemaId") UUID cinemaId);
}
