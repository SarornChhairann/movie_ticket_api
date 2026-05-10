package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.service.ShowtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/showtimes")
@RequiredArgsConstructor
public class ShowtimeApiController {

    private final ShowtimeService showtimeService;

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ShowtimeSearchResponse>> search(@RequestParam("movieId") Long movieId,
                                                               @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                               @RequestParam(value = "cinemaId", required = false) UUID cinemaId,
                                                               @RequestParam(value = "city", required = false) String city) {
        List<Showtime> showtimes = (cinemaId != null)
                ? showtimeService.findForMovieDateCinema(movieId, date, cinemaId)
                : showtimeService.findForMovieDateCity(movieId, date, (city == null) ? "" : city.trim());
        List<ShowtimeSearchResponse> payload = showtimes.stream()
                .filter(s -> !s.isBlocked())
                .map(s -> new ShowtimeSearchResponse(
                        s.getId(),
                        s.getShowTime().toString(),
                        s.getHall().getName(),
                        (s.getHall().getCinema() != null ? s.getHall().getCinema().getName() : s.getHall().getCity()),
                        s.getNormalPrice().toPlainString(),
                        s.getCouplePrice().toPlainString()
                ))
                .toList();
        return ResponseEntity.ok(payload);
    }

    @GetMapping(value = "/available-movies", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Long>> availableMovies(@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                      @RequestParam(value = "cinemaId", required = false) UUID cinemaId,
                                                      @RequestParam(value = "city", required = false) String city) {
        return ResponseEntity.ok(
                cinemaId != null
                        ? showtimeService.findAvailableMovieIdsByCinema(date, cinemaId)
                        : showtimeService.findAvailableMovieIds(date, (city == null) ? "" : city.trim())
        );
    }

    public record ShowtimeSearchResponse(Long id,
                                         String showTime,
                                         String hallName,
                                         String city,
                                         String normalPrice,
                                         String couplePrice) {
    }
}
