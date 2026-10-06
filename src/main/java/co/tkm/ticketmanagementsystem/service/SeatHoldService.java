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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatHoldService {

    public static final String SEAT_BOOKED = "SEAT_BOOKED";
    public static final String SEAT_HELD = "SEAT_HELD";
    public static final String HOLD_EXPIRED = "HOLD_EXPIRED";
    public static final String SEAT_LIMIT = "SEAT_LIMIT";
    public static final String HOLD_EXTEND_LIMIT = "HOLD_EXTEND_LIMIT";
    public static final int DEFAULT_HOLD_EXTEND_MINUTES = 5;
    public static final int DEFAULT_MAX_SEATS = 8;

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

    /** When the first of this session's holds on [seatIds] ends; empty when it holds none of them. */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> earliestExpiry(List<Long> seatIds, String sessionId) {
        if (seatIds == null || seatIds.isEmpty() || sessionId == null) return Optional.empty();
        return Optional.ofNullable(seatHoldRepository.findEarliestExpiry(sessionId, seatIds));
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

        // The cap covers what this session already holds on the show plus the new seats,
        // so repeated small holds cannot add up to a whole hall.
        List<SeatHold> current = seatHoldRepository
                .findActiveBySessionAndShowtime(sessionId, resolvedShowtimeId, LocalDateTime.now());
        Set<Long> afterHold = current.stream().map(SeatHold::getShowtimeSeatId).collect(Collectors.toSet());
        afterHold.addAll(showtimeSeatIds);
        requireWithinSeatLimit(afterHold.size(), getMaxSeats());

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

        // One clock per session and showtime (sprint 14): holding again — changed seats, or
        // the same seats — keeps the running expiry and the extended flag, so the timer
        // cannot be reset by re-holding and the single extend means something.
        int holdMinutes = getHoldMinutes();
        LocalDateTime expiresAt = current.stream().map(SeatHold::getExpiresAt).min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().plusMinutes(holdMinutes));
        boolean extended = current.stream().anyMatch(SeatHold::isExtended);
        seatHoldRepository.deleteBySessionIdAndSeatIds(sessionId, showtimeSeatIds);
        List<SeatHold> holds = showtimeSeatIds.stream()
                .map(seatId -> {
                    SeatHold hold = new SeatHold();
                    hold.setShowtimeSeatId(seatId);
                    hold.setSessionId(sessionId);
                    hold.setExpiresAt(expiresAt);
                    hold.setExtended(extended);
                    return hold;
                })
                .collect(Collectors.toList());
        seatHoldRepository.saveAll(holds);
        seatRealtimeService.publishHeld(resolvedShowtimeId, showtimeSeatIds, sessionId, expiresAt);
        return new HoldResult(resolvedShowtimeId, expiresAt, holdMinutes, extended);
    }

    /**
     * Adds {@code hold_extend_minutes} to every hold this session has on the showtime,
     * once (sprint 14). Runs under the same seat row lock as a hold, so it serializes
     * with holds and confirms. No active hold → 409 {@code HOLD_EXPIRED}; already
     * extended → 409 {@code HOLD_EXTEND_LIMIT}. Publishes {@code HELD} with the new expiry.
     */
    @Transactional
    public HoldResult extend(Long showtimeId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session is required.");
        }
        List<SeatHold> current = seatHoldRepository
                .findActiveBySessionAndShowtime(sessionId, showtimeId, LocalDateTime.now());
        if (current.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, HOLD_EXPIRED, "Your seat hold expired. Please reselect seats.");
        }
        List<Long> seatIds = current.stream().map(SeatHold::getShowtimeSeatId).sorted().toList();
        showtimeSeatRepository.findAllByShowtimeIdAndIdsForUpdate(showtimeId, seatIds);
        if (current.stream().anyMatch(SeatHold::isExtended)) {
            throw new ApiException(HttpStatus.CONFLICT, HOLD_EXTEND_LIMIT, "Your seat hold was already extended once.");
        }
        LocalDateTime expiresAt = current.stream().map(SeatHold::getExpiresAt).min(LocalDateTime::compareTo).orElseThrow()
                .plusMinutes(getHoldExtendMinutes());
        current.forEach(hold -> {
            hold.setExpiresAt(expiresAt);
            hold.setExtended(true);
        });
        seatHoldRepository.saveAll(current);
        seatRealtimeService.publishHeld(showtimeId, seatIds, sessionId, expiresAt);
        return new HoldResult(showtimeId, expiresAt, getHoldMinutes(), true);
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

    /** {@code max_seats_per_booking}; a missing or unusable value means {@link #DEFAULT_MAX_SEATS}. */
    public int getMaxSeats() {
        return effectiveMaxSeats(systemSettingService.getInt(
                SystemSettingService.MAX_SEATS_PER_BOOKING, DEFAULT_MAX_SEATS));
    }

    /**
     * Rejects [seatCount] above the seat cap with 400 {@code SEAT_LIMIT} (analytics
     * invariant 10). Every seat counts once, so a couple pair counts as two.
     */
    public static void requireWithinSeatLimit(int seatCount, int maxSeats) {
        int max = effectiveMaxSeats(maxSeats);
        if (seatCount > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, SEAT_LIMIT,
                    "You can book at most " + max + " seats at a time.");
        }
    }

    private static int effectiveMaxSeats(int configured) {
        return configured < 1 ? DEFAULT_MAX_SEATS : configured;
    }

    /** {@code hold_extend_minutes}; a missing or unusable value means {@link #DEFAULT_HOLD_EXTEND_MINUTES}. */
    public int getHoldExtendMinutes() {
        int n = systemSettingService.getInt(SystemSettingService.HOLD_EXTEND_MINUTES, DEFAULT_HOLD_EXTEND_MINUTES);
        return n < 1 ? DEFAULT_HOLD_EXTEND_MINUTES : n;
    }

    public record HoldResult(Long showtimeId, LocalDateTime expiresAt, int holdMinutes, boolean extended) {
    }
}
