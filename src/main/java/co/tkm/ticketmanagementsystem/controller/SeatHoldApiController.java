package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.service.SeatHoldService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * Short-lived seat holds. The hold owner is the {@code X-Session-Id} header (see
 * {@link HoldSessions}).
 *
 * <p>Every hold, release, expiry, and booking is also published over STOMP to
 * {@code /topic/showtimes/{showtimeId}/seats} as
 * {@code {type: HELD|RELEASED|BOOKED, showtimeId, seatIds, sessionId, expiresAtEpochMs}};
 * clients connect to {@code /ws} (SockJS; native clients use {@code /ws/websocket}).
 */
@RestController
@RequestMapping("/api/seat-holds")
@RequiredArgsConstructor
public class SeatHoldApiController {

    private final SeatHoldService seatHoldService;

    @PostMapping(value = "/hold", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HoldResponse> hold(@RequestBody HoldRequest req,
                                             @RequestHeader(value = HoldSessions.HEADER, required = false) String sessionHeader,
                                             HttpServletRequest request) {
        String sessionId = HoldSessions.resolve(sessionHeader, request);
        SeatHoldService.HoldResult result = seatHoldService.holdSeatsRealtime(req.showtimeId(), req.seatIds(), sessionId);
        return ResponseEntity.ok(new HoldResponse(result.showtimeId(), toEpochMs(result.expiresAt()), result.holdMinutes()));
    }

    @PostMapping(value = "/release", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReleaseResponse> release(@RequestBody ReleaseRequest req,
                                                   @RequestHeader(value = HoldSessions.HEADER, required = false) String sessionHeader,
                                                   HttpServletRequest request) {
        String sessionId = HoldSessions.resolve(sessionHeader, request);
        seatHoldService.releaseSeatsRealtime(req.showtimeId(), req.seatIds(), sessionId);
        return ResponseEntity.ok(new ReleaseResponse(true));
    }

    @GetMapping(value = "/state", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StateResponse> state(@RequestParam("showtimeId") Long showtimeId,
                                               @RequestHeader(value = HoldSessions.HEADER, required = false) String sessionHeader,
                                               HttpServletRequest request) {
        String sessionId = HoldSessions.resolve(sessionHeader, request);
        Set<Long> heldByOthers = seatHoldService.getHeldSeatIdsByOtherSessionsForShowtime(showtimeId, sessionId);
        Set<Long> heldByMe = seatHoldService.getHeldSeatIdsBySession(sessionId);
        return ResponseEntity.ok(new StateResponse(heldByOthers, heldByMe));
    }

    /**
     * Hold expiry is a {@code LocalDateTime.now()} in the JVM's zone, so it must be
     * converted with that same zone or the epoch is off by the zone difference.
     */
    static long toEpochMs(LocalDateTime expiresAt) {
        return expiresAt == null ? 0L : expiresAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public record HoldRequest(Long showtimeId, List<Long> seatIds) {
    }

    public record HoldResponse(Long showtimeId, long expiresAtEpochMs, int holdMinutes) {
    }

    public record ReleaseRequest(Long showtimeId, List<Long> seatIds) {
    }

    public record ReleaseResponse(boolean ok) {
    }

    public record StateResponse(Set<Long> heldByOthers, Set<Long> heldByMe) {
    }
}
