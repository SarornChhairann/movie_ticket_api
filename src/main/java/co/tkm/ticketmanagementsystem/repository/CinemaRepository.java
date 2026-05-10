package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.Cinema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CinemaRepository extends JpaRepository<Cinema, UUID> {
}

