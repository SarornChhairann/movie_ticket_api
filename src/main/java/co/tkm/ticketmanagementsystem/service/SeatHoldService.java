package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.SeatHold;
import co.tkm.ticketmanagementsystem.repository.SeatHoldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatHoldService {

    private final SeatHoldRepository seatHoldRepository;
    private final SystemSettingService systemSettingService;

    @Transactional
    public void holdSeats(List<Long> showtimeSeatIds, String sessionId) {
        seatHoldRepository.deleteBySessionIdAndSeatIds(sessionId, showtimeSeatIds);
        int holdMinutes = systemSettingService.getInt(SystemSettingService.HOLD_MINUTES, 10);
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
    public boolean hasActiveConflict(List<Long> seatIds, String sessionId) {
        return !seatHoldRepository.findActiveHoldsByOtherSession(seatIds, sessionId, LocalDateTime.now()).isEmpty();
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cleanupExpiredHolds() {
        seatHoldRepository.deleteExpired(LocalDateTime.now());
    }

    public int getHoldMinutes() {
        return systemSettingService.getInt(SystemSettingService.HOLD_MINUTES, 10);
    }
}
