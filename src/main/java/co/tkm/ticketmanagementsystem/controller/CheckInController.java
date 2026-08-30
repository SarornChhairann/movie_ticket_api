package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/checkin")
@RequiredArgsConstructor
@Tag(name = "Check-In", description = "Scan/enter a ticket code to check a customer in at the door")
public class CheckInController {

    private final TicketService ticketService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Check in a ticket by its code")
    public ResponseEntity<CheckInResponse> processCheckIn(@RequestBody CheckInRequest req) {
        String code = req.ticketCode() == null ? "" : req.ticketCode().trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Please enter a ticket code.");
        }
        Ticket ticket = ticketService.checkIn(code);
        return ResponseEntity.ok(new CheckInResponse(ticket.getTicketCode(), ticket.getStatus().name(),
                ticket.getScannedAt(), "Check-in successful!"));
    }

    public record CheckInRequest(String ticketCode) {
    }

    public record CheckInResponse(String ticketCode, String status,
                                   java.time.LocalDateTime scannedAt, String message) {
    }
}
