package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import org.springframework.http.HttpStatus;

import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.model.SeatHold;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.repository.SeatHoldRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatHoldService {

    public static final String SEAT_BOOKED = "SEAT_BOOKED";
    public static final String SEAT_HELD = "SEAT_HELD";
    public static final String HOLD_EXPIRED = "HOLD_EXPIRED";

    private final SeatHoldRepository seatHoldRepository;
    private final SystemSettingService systemSettingService;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final SeatRealtimeService seatRealtimeService;

    @Transactional
    public void holdSeats(List<Long> showtimeSeatIds, String sessionId) {
        holdSeatsRealtime(null, showtimeSeatIds, sessionId);
    }

    @Transactional
    public void releaseHolds(String sessionId) {
        seatHoldRepository.deleteBySessionId(sessionId);
    }

    @Transactional(readOnly = true)
    public Set<Long> getHeldSeatIdsByOtherSessions(String sessionId) {
        return seatHoldRepository.findHeldSeatIdsByOtherSessions(sessionId, LocalDateTime.now())
                .stream().collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Set<Long> getHeldSeatIdsBySession(String sessionId) {
        return seatHoldRepository.findActiveHeldSeatIdsBySession(sessionId, LocalDateTime.now())
                .stream().collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Set<Long> getHeldSeatIdsByOtherSessionsForShowtime(Long showtimeId, String sessionId) {
        String sid = (sessionId == null) ? "" : sessionId;
        return seatHoldRepository.findActiveHeldSeatIdsByShowtime(showtimeId, sid, LocalDateTime.now())
                .stream().collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public boolean hasActiveConflict(List<Long> seatIds, String sessionId) {
        return !seatHoldRepository.findActiveHoldsByOtherSession(seatIds, sessionId, LocalDateTime.now()).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean ownsActiveHolds(List<Long> seatIds, String sessionId) {
        if (seatIds == null || seatIds.isEmpty()) return false;
        if (sessionId == null || sessionId.isBlank()) return false;
        Set<Long> held = getHeldSeatIdsBySession(sessionId);
        for (Long id : seatIds) {
            if (id == null || !held.contains(id)) return false;
        }
        return true;
    }

    @Transactional
    public HoldResult holdSeatsRealtime(Long showtimeId, List<Long> showtimeSeatIds, String sessionId) {
        if (showtimeSeatIds == null || showtimeSeatIds.isEmpty()) {
            throw new IllegalArgumentException("No seats selected.");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session is required.");
        }

        Long resolvedShowtimeId = showtimeId;
        if (resolvedShowtimeId == null) {
            ShowtimeSeat any = showtimeSeatRepository.findById(showtimeSeatIds.get(0))
                    .orElseThrow(() -> new IllegalArgumentException("Seat not found."));
            resolvedShowtimeId = any.getShowtime().getId();
        }

        List<ShowtimeSeat> lockedSeats = showtimeSeatRepository
                .findAllByShowtimeIdAndIdsForUpdate(resolvedShowtimeId, showtimeSeatIds);
        if (lockedSeats.size() != showtimeSeatIds.size()) {
            throw new IllegalArgumentException("Some selected seats are invalid.");
        }
        for (ShowtimeSeat ss : lockedSeats) {
            if (ss.getStatus() != ShowtimeSeatStatus.AVAILABLE) {
                throw new ApiException(HttpStatus.CONFLICT, SEAT_BOOKED,
                        "Some seats were already booked. Please reselect.");
            }
        }
        CouplePairs.validate(lockedSeats);

        if (hasActiveConflict(showtimeSeatIds, sessionId)) {
            throw new ApiException(HttpStatus.CONFLICT, SEAT_HELD,
                    "Some seats are currently held by another user. Please reselect.");
        }

        seatHoldRepository.deleteBySessionIdAndSeatIds(sessionId, showtimeSeatIds);
        int holdMinutes = getHoldMinutes();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(holdMinutes);
        List<SeatHold> holds = showtimeSeatIds.stream()
                .map(seatId -> {
                    SeatHold hold = new SeatHold();
                    hold.setShowtimeSeatId(seatId);
                    hold.setSessionId(sessionId);
                    hold.setExpiresAt(expiresAt);
                    return hold;
                })
                .collect(Collectors.toList());
        seatHoldRepository.saveAll(holds);
        seatRealtimeService.publishHeld(resolvedShowtimeId, showtimeSeatIds, sessionId, expiresAt);
        return new HoldResult(resolvedShowtimeId, expiresAt, holdMinutes);
    }

    @Transactional
    public void releaseSeatsRealtime(Long showtimeId, List<Long> showtimeSeatIds, String sessionId) {
        if (showtimeSeatIds == null || showtimeSeatIds.isEmpty()) return;
        if (sessionId == null || sessionId.isBlank()) return;

        Long resolvedShowtimeId = showtimeId;
        if (resolvedShowtimeId == null) {
            ShowtimeSeat any = showtimeSeatRepository.findById(showtimeSeatIds.get(0))
                    .orElseThrow(() -> new IllegalArgumentException("Seat not found."));
            resolvedShowtimeId = any.getShowtime().getId();
        }

        seatHoldRepository.deleteBySessionIdAndSeatIds(sessionId, showtimeSeatIds);
        seatRealtimeService.publishReleased(resolvedShowtimeId, showtimeSeatIds, sessionId);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cleanupExpiredHolds() {
        LocalDateTime now = LocalDateTime.now();
        List<SeatHoldRepository.ExpiredHoldRow> expired = seatHoldRepository.findExpiredWithShowtimeId(now);
        if (expired == null || expired.isEmpty()) {
            return;
        }
        Map<Long, List<Long>> byShowtime = new HashMap<>();
        Map<Long, String> anySessionByShowtime = new HashMap<>();
        for (SeatHoldRepository.ExpiredHoldRow row : expired) {
            if (row.getShowtimeId() == null || row.getShowtimeSeatId() == null) continue;
            byShowtime.computeIfAbsent(row.getShowtimeId(), __ -> new java.util.ArrayList<>())
                    .add(row.getShowtimeSeatId());
            if (row.getSessionId() != null) {
                anySessionByShowtime.putIfAbsent(row.getShowtimeId(), row.getSessionId());
            }
        }
        seatHoldRepository.deleteExpired(now);
        for (Map.Entry<Long, List<Long>> e : byShowtime.entrySet()) {
            seatRealtimeService.publishReleased(e.getKey(), e.getValue(), anySessionByShowtime.get(e.getKey()));
        }
    }

    public int getHoldMinutes() {
        return systemSettingService.getInt(SystemSettingService.HOLD_MINUTES, 10);
    }

    public record HoldResult(Long showtimeId, LocalDateTime expiresAt, int holdMinutes) {
    }
}
