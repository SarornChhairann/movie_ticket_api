package co.tkm.ticketmanagementsystem.controller.admin;

import co.tkm.ticketmanagementsystem.enums.PaymentStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.service.BookingService;
import co.tkm.ticketmanagementsystem.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/bookings")
@RequiredArgsConstructor
@Tag(name = "Admin: Bookings", description = "Back-office booking management")
public class AdminBookingController {

    private final BookingService bookingService;
    private final ReportService reportService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List bookings, optionally filtered by a search query or a payment status",
            description = "paymentStatus is one of PENDING, PAID, FAILED, REFUND_PENDING, REFUNDED; "
                    + "when both are given, the search runs inside that payment status.")
    @Transactional(readOnly = true)
    public ResponseEntity<List<BookingSummary>> bookings(@RequestParam(required = false) String q,
                                                         @RequestParam(required = false) PaymentStatus paymentStatus) {
        List<Booking> bookings = (q != null && !q.isBlank())
                ? bookingService.searchBookings(q)
                : paymentStatus != null
                        ? bookingService.getBookingsByPaymentStatus(paymentStatus)
                        : bookingService.getAllBookingsForAdmin();
        Map<Long, PaymentStatus> payments = bookingService.paymentStatuses(
                bookings.stream().map(Booking::getId).toList());
        return ResponseEntity.ok(bookings.stream()
                .filter(b -> paymentStatus == null || paymentStatus == payments.get(b.getId()))
                .map(b -> BookingSummary.from(b, payments.get(b.getId())))
                .toList());
    }

    @PostMapping(value = "/{id}/refund", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Mark a cancelled booking's payment as refunded",
            description = "REFUND_PENDING becomes REFUNDED. 409 when the booking has no refund pending.")
    public ResponseEntity<Map<String, Object>> markRefunded(@PathVariable Long id) {
        bookingService.markRefunded(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Booking #" + id + " refunded."));
    }

    @PostMapping(value = "/{id}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cancel a booking as an admin")
    public ResponseEntity<Map<String, Object>> adminCancelBooking(@PathVariable Long id) {
        bookingService.adminCancelBooking(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Booking #" + id + " cancelled."));
    }

    @GetMapping("/export")
    @Operation(summary = "Export bookings as CSV")
    public ResponseEntity<byte[]> exportBookingsCsv(@RequestParam(required = false) String q) {
        byte[] csv = reportService.generateBookingsCsv(q);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bookings.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
