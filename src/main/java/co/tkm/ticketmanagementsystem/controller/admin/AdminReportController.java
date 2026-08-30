package co.tkm.ticketmanagementsystem.controller.admin;

import co.tkm.ticketmanagementsystem.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Admin: Reports", description = "Back-office revenue and booking reports")
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get revenue/booking reports and daily stats")
    public ResponseEntity<ReportResponse> reports() {
        return ResponseEntity.ok(new ReportResponse(
                reportService.getRevenueToday(), reportService.getRevenueThisWeek(), reportService.getRevenueThisMonth(),
                reportService.getTopMovies(10), reportService.getDailyStats(30)));
    }

    @GetMapping("/export")
    @Operation(summary = "Export a bookings report as CSV")
    public ResponseEntity<byte[]> exportReportCsv(@RequestParam(required = false) String q) {
        byte[] csv = reportService.generateBookingsCsv(q);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    public record ReportResponse(BigDecimal revenueToday, BigDecimal revenueWeek, BigDecimal revenueMonth,
                                  List<Map<String, Object>> topMovies, List<Map<String, Object>> dailyStats) {
    }
}
