package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.service.BookingService;
import co.tkm.ticketmanagementsystem.service.HallService;
import co.tkm.ticketmanagementsystem.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Tag(name = "Movies", description = "Browse movies, filter listings, and view showtimes for a movie")
public class MovieController {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Phnom_Penh");

    private final MovieService movieService;
    private final ShowtimeRepository showtimeRepository;
    private final HallService hallService;
    private final BookingService bookingService;

    @GetMapping(value = "/movies", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List movies, optionally filtered by tab/genre/language/format/rating/search/city")
    public ResponseEntity<MovieListResponse> list(
            @RequestParam(value = "tab", required = false, defaultValue = "NOW_SHOWING") String tab,
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "format", required = false) String format,
            @RequestParam(value = "rating", required = false) String rating,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "city", required = false) String city,
            Authentication authentication) {

        MovieStatus status;
        try {
            status = MovieStatus.valueOf(tab);
        } catch (IllegalArgumentException e) {
            status = MovieStatus.NOW_SHOWING;
        }

        List<Movie> movies = movieService.filterMovies(status, genre, language, format, rating, search);
        List<Movie> comingSoon = movieService.findComingSoon();
        List<Hall> halls = hallService.findByCity(city);
        List<String> cities = hallService.findDistinctCities();
        Movie featuredMovie = movieService.findFeaturedMovie().orElse(null);

        Long userBookingCount = null;
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin) {
                userBookingCount = bookingService.countBookingsForUser(authentication.getName());
            }
        }

        List<HallSummary> hallSummaries = halls.stream()
                .map(h -> new HallSummary(h.getId(), h.getName(), h.getCity()))
                .toList();

        return ResponseEntity.ok(new MovieListResponse(
                movies, comingSoon, hallSummaries, cities, city, status.name(),
                genre, language, format, rating, search,
                movieService.findDistinctGenres(), movieService.findDistinctLanguages(),
                featuredMovie, userBookingCount));
    }

    @GetMapping(value = "/movies/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get movie detail with showtimes grouped by date, optionally filtered by city/cinema/date/hall")
    @Transactional(readOnly = true)
    public ResponseEntity<MovieDetailResponse> detail(@PathVariable Long id,
                         @RequestParam(value = "city", required = false) String city,
                         @RequestParam(value = "cinemaId", required = false) UUID cinemaId,
                         @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam(value = "hallId", required = false) Long hallId) {
        Movie movie = movieService.findById(id);
        // Customers only see showtimes they can still book: not blocked, not started.
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        List<Showtime> showtimes = showtimeRepository.findByMovieIdWithHall(id).stream()
                .filter(s -> isBookable(s, now))
                .collect(Collectors.toList());

        if (cinemaId != null) {
            showtimes = showtimes.stream()
                    .filter(s -> s.getHall() != null
                            && s.getHall().getCinema() != null
                            && cinemaId.equals(s.getHall().getCinema().getId()))
                    .collect(Collectors.toList());
        } else
        if (city != null && !city.isBlank()) {
            showtimes = showtimes.stream()
                    .filter(s -> s.getHall() != null
                            && s.getHall().getCity() != null
                            && city.equalsIgnoreCase(s.getHall().getCity()))
                    .collect(Collectors.toList());
        }

        List<LocalDate> availableDates = showtimes.stream()
                .map(Showtime::getShowDate)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        LocalDate selectedDate = date != null
                ? date
                : (movie.getStatus() == MovieStatus.NOW_SHOWING && !availableDates.isEmpty()
                    ? availableDates.get(0)
                    : null);

        List<Showtime> showtimesForSelector = showtimes;
        if (selectedDate != null) {
            showtimesForSelector = showtimesForSelector.stream()
                    .filter(s -> selectedDate.equals(s.getShowDate()))
                    .collect(Collectors.toList());
        }

        List<Hall> availableHalls = showtimesForSelector.stream()
                .map(Showtime::getHall)
                .filter(h -> h != null && h.getId() != null)
                .distinct()
                .sorted(Comparator.comparing(Hall::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());

        List<Showtime> filteredShowtimes = showtimesForSelector;
        if (hallId != null) {
            filteredShowtimes = filteredShowtimes.stream()
                    .filter(s -> s.getHall() != null && hallId.equals(s.getHall().getId()))
                    .collect(Collectors.toList());
        }

        Map<LocalDate, List<ShowtimeSummary>> grouped = filteredShowtimes.stream()
                .collect(Collectors.groupingBy(Showtime::getShowDate, LinkedHashMap::new,
                        Collectors.mapping(this::toShowtimeSummary, Collectors.toList())));

        List<String> cities = hallService.findDistinctCities();
        List<HallSummary> hallSummaries = availableHalls.stream()
                .map(h -> new HallSummary(h.getId(), h.getName(), h.getCity()))
                .toList();

        return ResponseEntity.ok(new MovieDetailResponse(
                movie, grouped, cities, city, cinemaId,
                availableDates, selectedDate, hallSummaries, hallId));
    }

    static boolean isBookable(Showtime s, LocalDateTime now) {
        if (s.isBlocked() || s.getShowDate() == null) {
            return false;
        }
        LocalTime time = s.getShowTime() != null ? s.getShowTime() : LocalTime.MIDNIGHT;
        return LocalDateTime.of(s.getShowDate(), time).isAfter(now);
    }

    private ShowtimeSummary toShowtimeSummary(Showtime s) {
        Hall hall = s.getHall();
        String cinemaName = hall == null ? null
                : (hall.getCinema() != null ? hall.getCinema().getName() : hall.getCity());
        return new ShowtimeSummary(
                s.getId(),
                s.getShowTime() != null ? s.getShowTime().toString() : null,
                hall != null ? hall.getId() : null,
                hall != null ? hall.getName() : null,
                s.getNormalPrice(),
                s.getCouplePrice(),
                s.isBlocked(),
                cinemaName,
                hall != null && hall.isHasDolbyAtmos(),
                hall != null && hall.isHasScreenX());
    }

    public record HallSummary(Long id, String name, String city) {
    }

    public record ShowtimeSummary(Long id, String showTime, Long hallId, String hallName,
                                   java.math.BigDecimal normalPrice, java.math.BigDecimal couplePrice,
                                   boolean blocked, String cinemaName, boolean hasDolbyAtmos,
                                   boolean hasScreenX) {
    }

    public record MovieListResponse(List<Movie> movies,
                                     List<Movie> comingSoon,
                                     List<HallSummary> halls,
                                     List<String> cities,
                                     String selectedCity,
                                     String tab,
                                     String genre,
                                     String language,
                                     String format,
                                     String rating,
                                     String search,
                                     List<String> genres,
                                     List<String> languages,
                                     Movie featuredMovie,
                                     Long userBookingCount) {
    }

    public record MovieDetailResponse(Movie movie,
                                       Map<LocalDate, List<ShowtimeSummary>> showtimesByDate,
                                       List<String> cities,
                                       String selectedCity,
                                       UUID selectedCinemaId,
                                       List<LocalDate> availableDates,
                                       LocalDate selectedDate,
                                       List<HallSummary> availableHalls,
                                       Long selectedHallId) {
    }
}
