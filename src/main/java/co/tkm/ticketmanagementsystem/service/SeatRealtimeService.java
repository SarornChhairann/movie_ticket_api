package co.tkm.ticketmanagementsystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatRealtimeService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Phnom_Penh");

    private final SimpMessagingTemplate messagingTemplate;

    public void publishHeld(Long showtimeId, List<Long> showtimeSeatIds, String sessionId, LocalDateTime expiresAt) {
        publish(showtimeId, new SeatEvent("HELD", showtimeId, showtimeSeatIds, sessionId,
                expiresAt == null ? null : expiresAt.atZone(APP_ZONE).toInstant().toEpochMilli()));
    }

    public void publishReleased(Long showtimeId, List<Long> showtimeSeatIds, String sessionId) {
        publish(showtimeId, new SeatEvent("RELEASED", showtimeId, showtimeSeatIds, sessionId, null));
    }

    public void publishBooked(Long showtimeId, List<Long> showtimeSeatIds) {
        publish(showtimeId, new SeatEvent("BOOKED", showtimeId, showtimeSeatIds, null, null));
    }

    private void publish(Long showtimeId, SeatEvent event) {
        if (showtimeId == null || event == null) return;
        messagingTemplate.convertAndSend("/topic/showtimes/" + showtimeId + "/seats", event);
    }

    public record SeatEvent(String type, Long showtimeId, List<Long> seatIds, String sessionId, Long expiresAtEpochMs) {
    }
}

