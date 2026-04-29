package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByHall(Hall hall);

    List<Seat> findByHallAndSeatType(Hall hall, SeatType type);
}
