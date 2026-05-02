package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.Hall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HallRepository extends JpaRepository<Hall, Long> {

    @Query("SELECT DISTINCT h.city FROM Hall h WHERE h.city IS NOT NULL AND h.city <> '' ORDER BY h.city")
    List<String> findDistinctCities();

    List<Hall> findByCityIgnoreCase(String city);
}
