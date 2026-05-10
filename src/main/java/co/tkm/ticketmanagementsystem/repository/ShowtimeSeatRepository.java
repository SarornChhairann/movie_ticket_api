package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ShowtimeSeatRepository extends JpaRepository<ShowtimeSeat, Long> {
    List<ShowtimeSeat> findByShowtime(Showtime showtime);
    List<ShowtimeSeat> findByShowtimeOrderBySeatRowNumberAscSeatIdAsc(Showtime showtime);

    Optional<ShowtimeSeat> findByShowtimeAndSeat(Showtime showtime, Seat seat);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ss from ShowtimeSeat ss where ss.showtime.id = :showtimeId and ss.id in :ids")
    List<ShowtimeSeat> findAllByShowtimeIdAndIdsForUpdate(@Param("showtimeId") Long showtimeId, @Param("ids") List<Long> ids);

    @Query("select ss.id from ShowtimeSeat ss where ss.showtime.id = :showtimeId")
    List<Long> findIdsByShowtimeId(@Param("showtimeId") Long showtimeId);

    void deleteByShowtimeId(Long showtimeId);
}
