package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.enums.TicketStatus;
import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.service.PdfTicketService;
import co.tkm.ticketmanagementsystem.service.QrCodeService;
import co.tkm.ticketmanagementsystem.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "Look up tickets by code, and fetch their QR code / PDF")
public class TicketController {

    private final TicketService ticketService;
    private final QrCodeService qrCodeService;
    private final PdfTicketService pdfTicketService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Find a ticket by its ticket code (query param)")
    public ResponseEntity<TicketResponse> searchByCode(@RequestParam("ticketCode") String ticketCode) {
        String normalizedCode = ticketCode == null ? "" : ticketCode.trim();
        if (normalizedCode.isEmpty()) {
            throw new IllegalArgumentException("Please enter a ticket number.");
        }
        return ResponseEntity.ok(toResponse(ticketService.findByTicketCode(normalizedCode)));
    }

    @GetMapping(value = "/{ticketCode}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a ticket by its ticket code (path param)")
    public ResponseEntity<TicketResponse> viewTicket(@PathVariable String ticketCode) {
        return ResponseEntity.ok(toResponse(ticketService.findByTicketCode(ticketCode)));
    }

    @GetMapping(value = "/{ticketCode}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Get the ticket's QR code as a PNG image")
    public ResponseEntity<byte[]> qrCode(@PathVariable String ticketCode) {
        ticketService.findByTicketCode(ticketCode); // validate exists
        byte[] png = qrCodeService.generateQrPng(ticketCode, 250);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @GetMapping("/{ticketCode}/pdf")
    @Operation(summary = "Download the ticket as a PDF")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String ticketCode) {
        Ticket ticket = ticketService.findByTicketCode(ticketCode);
        byte[] pdf = pdfTicketService.generatePdf(ticket);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"ticket-" + ticketCode + ".pdf\"")
                .body(pdf);
    }

    private TicketResponse toResponse(Ticket ticket) {
        var booking = ticket.getBooking();
        var showtime = booking.getShowtime();
        String holderName = booking.getUser() != null ? booking.getUser().getName() : booking.getGuestName();
        return new TicketResponse(
                ticket.getId(),
                ticket.getTicketCode(),
                ticket.getStatus(),
                ticket.getIssuedAt(),
                ticket.getScannedAt(),
                ticket.getSeat().getSeatCode(),
                ticket.getSeat().getSeatType().name(),
                booking.getId(),
                showtime.getMovie().getTitle(),
                showtime.getHall().getName(),
                showtime.getShowDate(),
                showtime.getShowTime(),
                holderName);
    }

    public record TicketResponse(Long id,
                                  String ticketCode,
                                  TicketStatus status,
                                  LocalDateTime issuedAt,
                                  LocalDateTime scannedAt,
                                  String seatCode,
                                  String seatType,
                                  Long bookingId,
                                  String movieTitle,
                                  String hallName,
                                  LocalDate showDate,
                                  LocalTime showTime,
                                  String holderName) {
    }
}
