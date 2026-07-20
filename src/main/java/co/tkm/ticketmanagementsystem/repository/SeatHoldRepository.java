package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.SeatHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {

    List<SeatHold> findBySessionId(String sessionId);

    @Query("SELECT sh FROM SeatHold sh WHERE sh.showtimeSeatId IN :seatIds " +
           "AND sh.sessionId <> :sessionId AND sh.expiresAt > :now")
    List<SeatHold> findActiveHoldsByOtherSession(@Param("seatIds") List<Long> seatIds,
                                                  @Param("sessionId") String sessionId,
                                                  @Param("now") LocalDateTime now);

    @Query("SELECT sh.showtimeSeatId FROM SeatHold sh WHERE sh.sessionId <> :sessionId " +
           "AND sh.expiresAt > :now")
    List<Long> findHeldSeatIdsByOtherSessions(@Param("sessionId") String sessionId,
                                               @Param("now") LocalDateTime now);

    @Query("""
        SELECT sh.showtimeSeatId
        FROM SeatHold sh, ShowtimeSeat ss
        WHERE ss.id = sh.showtimeSeatId
          AND ss.showtime.id = :showtimeId
          AND sh.expiresAt > :now
          AND (:sessionId = '' OR sh.sessionId <> :sessionId)
        """)
    List<Long> findActiveHeldSeatIdsByShowtime(@Param("showtimeId") Long showtimeId,
                                               @Param("sessionId") String sessionId,
                                               @Param("now") LocalDateTime now);

    @Query("""
        SELECT sh.showtimeSeatId FROM SeatHold sh
        WHERE sh.sessionId = :sessionId
          AND sh.expiresAt > :now
        """)
    List<Long> findActiveHeldSeatIdsBySession(@Param("sessionId") String sessionId,
                                              @Param("now") LocalDateTime now);

    @Query("""
        SELECT ss.showtime.id AS showtimeId, sh.showtimeSeatId AS showtimeSeatId, sh.sessionId AS sessionId
        FROM SeatHold sh, ShowtimeSeat ss
        WHERE ss.id = sh.showtimeSeatId
          AND sh.expiresAt < :now
        """)
    List<ExpiredHoldRow> findExpiredWithShowtimeId(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM SeatHold sh WHERE sh.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM SeatHold sh WHERE sh.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") String sessionId);

    @Modifying
    @Query("DELETE FROM SeatHold sh WHERE sh.showtimeSeatId IN :seatIds AND sh.sessionId = :sessionId")
    void deleteBySessionIdAndSeatIds(@Param("sessionId") String sessionId,
                                     @Param("seatIds") List<Long> seatIds);

    @Modifying
    @Query("DELETE FROM SeatHold sh WHERE sh.showtimeSeatId IN :seatIds")
    void deleteBySeatIds(@Param("seatIds") List<Long> seatIds);

    interface ExpiredHoldRow {
        Long getShowtimeId();
        Long getShowtimeSeatId();
        String getSessionId();
    }
}
