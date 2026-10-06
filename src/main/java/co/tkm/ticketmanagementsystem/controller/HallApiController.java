package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.model.Cinema;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.HallRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Customer hall browsing (sprint 10): every hall with its cinema, and one hall's
 * bookable schedule. Public and read-only; admin hall management stays on
 * {@code /admin/halls}.
 */
@RestController
@RequestMapping("/api/halls")
@RequiredArgsConstructor
@Tag(name = "Halls", description = "Browse halls and a hall's bookable showtimes")
public class HallApiController {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Phnom_Penh");

    private final HallRepository hallRepository;
    private final ShowtimeRepository showtimeRepository;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List every hall with its cinema, ordered by cinema name then hall name")
    @Transactional(readOnly = true)
    public ResponseEntity<List<HallRow>> list() {
        return ResponseEntity.ok(hallRepository.findAllWithCinemaOrdered().stream().map(HallApiController::toRow).toList());
    }

    @GetMapping(value = "/{id}/showtimes", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "A hall's bookable showtimes for one date",
            description = "Bookable = not blocked and starting after now in Asia/Phnom_Penh. "
                    + "selectedDate is the requested date when it has bookable shows, else the first such date, "
                    + "else null. 404 for an unknown hall.")
    @Transactional(readOnly = true)
    public ResponseEntity<HallSchedule> schedule(@PathVariable Long id,
                                                 @RequestParam(value = "date", required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Hall hall = hallRepository.findByIdWithCinema(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found: " + id));

        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        List<Showtime> bookable = showtimeRepository.findUnblockedByHallFrom(id, now.toLocalDate()).stream()
                .filter(s -> MovieController.isBookable(s, now))
                .toList();

        List<LocalDate> availableDates = bookable.stream().map(Showtime::getShowDate).distinct().sorted().toList();
        LocalDate selected = date != null && availableDates.contains(date)
                ? date
                : (availableDates.isEmpty() ? null : availableDates.get(0));

        List<HallShowtime> showtimes = bookable.stream()
                .filter(s -> s.getShowDate().equals(selected))
                .map(HallApiController::toShowtime)
                .toList();
        return ResponseEntity.ok(new HallSchedule(toRow(hall), availableDates, selected, showtimes));
    }

    private static HallRow toRow(Hall h) {
        Cinema c = h.getCinema();
        return new HallRow(h.getId(), h.getName(), h.getType(), h.getCity(), h.getAddress(), h.getTotalSeats(),
                h.isHasDolbyAtmos(), h.isHasScreenX(), h.getAmenities(),
                c != null && c.getId() != null ? c.getId().toString() : null,
                c != null ? c.getName() : null,
                c != null ? c.getOpeningHours() : null);
    }

    private static HallShowtime toShowtime(Showtime s) {
        return new HallShowtime(s.getId(), s.getShowDate(),
                s.getShowTime() != null ? s.getShowTime().toString() : null,
                s.getMovie().getId(), s.getMovie().getTitle(), s.getMovie().getImagePath(),
                s.getNormalPrice(), s.getCouplePrice());
    }

    /** One hall as the customer sees it; no coordinates. */
    public record HallRow(Long id, String name, String type, String city, String address, Integer totalSeats,
                          boolean hasDolbyAtmos, boolean hasScreenX, String amenities,
                          String cinemaId, String cinemaName, String openingHours) {
    }

    public record HallShowtime(Long id, LocalDate showDate, String showTime, Long movieId, String movieTitle,
                               String imagePath, BigDecimal normalPrice, BigDecimal couplePrice) {
    }

    public record HallSchedule(HallRow hall, List<LocalDate> availableDates, LocalDate selectedDate,
                               List<HallShowtime> showtimes) {
    }
}
