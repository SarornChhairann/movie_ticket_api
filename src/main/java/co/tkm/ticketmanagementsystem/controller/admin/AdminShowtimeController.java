package co.tkm.ticketmanagementsystem.controller.admin;

import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.service.HallService;
import co.tkm.ticketmanagementsystem.service.MovieService;
import co.tkm.ticketmanagementsystem.service.NotificationService;
import co.tkm.ticketmanagementsystem.service.ShowtimeService;
import co.tkm.ticketmanagementsystem.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/admin/showtimes")
@RequiredArgsConstructor
@Tag(name = "Admin: Showtimes", description = "Back-office showtime management")
public class AdminShowtimeController {

    private final ShowtimeService showtimeService;
    private final MovieService movieService;
    private final HallService hallService;
    private final BookingRepository bookingRepository;
    private final SystemSettingService systemSettingService;
    private final NotificationService notificationService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List all showtimes with booking counts and edit-lock status")
    @Transactional(readOnly = true)
    public ResponseEntity<ShowtimeListResponse> showtimes() {
        List<Showtime> showtimes = showtimeService.findAll();
        int editLockHours = systemSettingService.getInt(SystemSettingService.CANCELLATION_HOURS, 2);
        ZoneId zone = ZoneId.of("Asia/Phnom_Penh");
        LocalDateTime lockCutoff = LocalDateTime.now(zone).plusHours(Math.max(0, editLockHours));

        List<ShowtimeResponse> items = showtimes.stream().map(s -> {
            long bookingCount = bookingRepository.countByShowtimeId(s.getId());
            long activeBookingCount = bookingRepository.countByShowtimeIdAndStatusNot(s.getId(), BookingStatus.CANCELLED);
            boolean editLocked = s.getShowDate() != null && s.getShowTime() != null
                    && !LocalDateTime.of(s.getShowDate(), s.getShowTime()).isAfter(lockCutoff);
            return toShowtimeResponse(s, bookingCount, activeBookingCount, editLocked);
        }).toList();

        return ResponseEntity.ok(new ShowtimeListResponse(items, editLockHours));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a showtime by id")
    @Transactional(readOnly = true)
    public ResponseEntity<ShowtimeResponse> showtime(@PathVariable Long id) {
        return ResponseEntity.ok(toShowtimeResponse(showtimeService.findById(id), null, null, false));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create or update a showtime (set id to update); optionally broadcasts a notification")
    @Transactional(readOnly = true)
    public ResponseEntity<ShowtimeResponse> saveShowtime(@RequestBody ShowtimeRequest req) {
        Showtime showtime = new Showtime();
        showtime.setId(req.id());
        showtime.setMovie(movieService.findById(req.movieId()));
        showtime.setHall(hallService.findById(req.hallId()));
        showtime.setShowDate(req.showDate());
        showtime.setShowTime(req.showTime());
        showtime.setNormalPrice(req.normalPrice());
        showtime.setCouplePrice(req.couplePrice());
        showtime.setBlocked(!req.publishNow());

        Showtime saved = showtimeService.save(showtime);

        if (req.publishNow() && req.sendNotification()) {
            notificationService.sendBroadcast(
                    "New movie available!",
                    saved.getMovie().getTitle() + " is now available. " +
                    "Showtime: " + saved.getShowDate() + " " + saved.getShowTime() +
                    " at " + saved.getHall().getName() + ".");
        }

        return ResponseEntity.ok(toShowtimeResponse(saved, null, null, false));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a showtime (only allowed while blocked and booking-free)")
    public ResponseEntity<Void> deleteShowtime(@PathVariable Long id) {
        showtimeService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/block", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Toggle a showtime's blocked/published status")
    @Transactional(readOnly = true)
    public ResponseEntity<ShowtimeResponse> toggleBlockShowtime(@PathVariable Long id) {
        showtimeService.toggleBlocked(id);
        return ResponseEntity.ok(toShowtimeResponse(showtimeService.findById(id), null, null, false));
    }

    @PostMapping(value = "/{id}/copy-next-week", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Copy a showtime to the same day/time next week")
    @Transactional(readOnly = true)
    public ResponseEntity<ShowtimeResponse> copyShowtimeNextWeek(@PathVariable Long id) {
        Showtime copy = showtimeService.copyToNextWeek(id);
        return ResponseEntity.ok(toShowtimeResponse(copy, null, null, false));
    }

    private ShowtimeResponse toShowtimeResponse(Showtime s, Long bookingCount, Long activeBookingCount, boolean editLocked) {
        return new ShowtimeResponse(s.getId(), s.getMovie().getId(), s.getMovie().getTitle(),
                s.getHall().getId(), s.getHall().getName(), s.getShowDate(), s.getShowTime(),
                s.getNormalPrice(), s.getCouplePrice(), s.isBlocked(), bookingCount, activeBookingCount, editLocked);
    }

    public record ShowtimeResponse(Long id, Long movieId, String movieTitle, Long hallId, String hallName,
                                    LocalDate showDate, LocalTime showTime, BigDecimal normalPrice,
                                    BigDecimal couplePrice, boolean blocked, Long bookingCount,
                                    Long activeBookingCount, boolean editLocked) {
    }

    public record ShowtimeListResponse(List<ShowtimeResponse> showtimes, int editLockHours) {
    }

    public record ShowtimeRequest(Long id, Long movieId, Long hallId, LocalDate showDate, LocalTime showTime,
                                   BigDecimal normalPrice, BigDecimal couplePrice, boolean publishNow,
                                   boolean sendNotification) {
    }
}
