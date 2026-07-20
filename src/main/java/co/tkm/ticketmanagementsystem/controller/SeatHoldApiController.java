package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.service.SeatHoldService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/seat-holds")
@RequiredArgsConstructor
public class SeatHoldApiController {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Phnom_Penh");

    private final SeatHoldService seatHoldService;

    @PostMapping(value = "/hold", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HoldResponse> hold(@RequestBody HoldRequest req, HttpSession session) {
        String sessionId = session.getId();
        SeatHoldService.HoldResult result = seatHoldService.holdSeatsRealtime(req.showtimeId(), req.seatIds(), sessionId);
        long expiresAtEpochMs = result.expiresAt() == null
                ? 0L
                : result.expiresAt().atZone(APP_ZONE).toInstant().toEpochMilli();
        return ResponseEntity.ok(new HoldResponse(result.showtimeId(), expiresAtEpochMs, result.holdMinutes()));
    }

    @PostMapping(value = "/release", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReleaseResponse> release(@RequestBody ReleaseRequest req, HttpSession session) {
        String sessionId = session.getId();
        seatHoldService.releaseSeatsRealtime(req.showtimeId(), req.seatIds(), sessionId);
        return ResponseEntity.ok(new ReleaseResponse(true));
    }

    @GetMapping(value = "/state", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StateResponse> state(@RequestParam("showtimeId") Long showtimeId, HttpSession session) {
        String sessionId = session.getId();
        Set<Long> heldByOthers = seatHoldService.getHeldSeatIdsByOtherSessionsForShowtime(showtimeId, sessionId);
        Set<Long> heldByMe = seatHoldService.getHeldSeatIdsBySession(sessionId);
        return ResponseEntity.ok(new StateResponse(heldByOthers, heldByMe));
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

