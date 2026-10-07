package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.service.MovieBrowseCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieBrowseApiController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Phnom_Penh");
    private static final int MAX_CHIPS = 4;

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final MovieBrowseCacheService cacheService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<MovieBrowseResponse>> browse(@RequestParam("type") String type,
                                                           @RequestParam(value = "date", required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                           @RequestParam(value = "month", required = false) String month,
                                                           @RequestParam(value = "cinemaId", required = false) UUID cinemaId,
                                                           @RequestParam(value = "city", required = false) String city) {
        String t = (type == null) ? "" : type.trim().toLowerCase();
        String c = (city == null) ? "" : city.trim();

        if ("now_showing".equals(t) || "now-showing".equals(t) || "nowshowing".equals(t)) {
            if (date == null) {
                throw new IllegalArgumentException("date is required for now_showing.");
            }
            String key = "now_showing|" + date + "|" + (cinemaId != null ? cinemaId : c.toLowerCase());
            List<MovieBrowseResponse> all = cacheService.getOrLoad(key, () -> nowShowing(date, cinemaId, c));
            // After the cache read: a cached day must not keep serving showtimes that have started.
            return ResponseEntity.ok(upcoming(all, LocalDateTime.now(APP_ZONE)));
        }

        if ("coming_soon".equals(t) || "coming-soon".equals(t) || "comingsoon".equals(t)) {
            if (month == null || month.isBlank()) {
                throw new IllegalArgumentException("month is required for coming_soon.");
            }
            YearMonth ym = YearMonth.parse(month);
            String key = "coming_soon|" + ym + "|" + (cinemaId != null ? cinemaId : c.toLowerCase());
            return ResponseEntity.ok(cacheService.getOrLoad(key, () -> comingSoon(ym)));
        }

        throw new IllegalArgumentException("Unknown type: " + type);
    }

    private List<MovieBrowseResponse> nowShowing(LocalDate date, UUID cinemaId, String city) {
        List<Showtime> showtimes = (cinemaId != null)
                ? showtimeRepository.findByShowDateCinemaNotBlocked(date, cinemaId)
                : showtimeRepository.findByShowDateCityNotBlocked(date, normalizeCity(city));
        Map<Long, MovieGroup> grouped = new LinkedHashMap<>();
        for (Showtime s : showtimes) {
            if (s.getMovie() == null || s.getMovie().getId() == null) continue;
            Long movieId = s.getMovie().getId();
            MovieGroup g = grouped.computeIfAbsent(movieId, __ -> new MovieGroup(s.getMovie(), new ArrayList<>()));
            g.showtimes.add(toChip(s));
        }

        // Every chip is cached; the started ones are dropped and the list capped per request.
        List<MovieBrowseResponse> out = new ArrayList<>();
        for (MovieGroup g : grouped.values()) {
            g.showtimes.sort(Comparator.comparing(ShowtimeChip::time));
            out.add(toResponse(g.movie, List.copyOf(g.showtimes)));
        }
        return out;
    }

    private static ShowtimeChip toChip(Showtime s) {
        var hall = s.getHall();
        String cinemaName = hall == null ? "" : (hall.getCinema() != null ? hall.getCinema().getName() : hall.getCity());
        return new ShowtimeChip(
                s.getId(),
                s.getShowTime() != null ? TIME_FMT.format(s.getShowTime()) : "",
                s.getShowDate() != null ? s.getShowDate().toString() : "",
                hall == null ? "" : hall.getName(),
                cinemaName == null ? "" : cinemaName,
                hall != null && hall.isHasDolbyAtmos(),
                hall != null && hall.isHasScreenX());
    }

    /**
     * Keeps only showtimes that start after {@code now} (cinema zone), at most four per movie,
     * and drops movies left with none.
     */
    static List<MovieBrowseResponse> upcoming(List<MovieBrowseResponse> all, LocalDateTime now) {
        List<MovieBrowseResponse> out = new ArrayList<>();
        for (MovieBrowseResponse m : all) {
            List<ShowtimeChip> chips = m.showtimes().stream()
                    .filter(chip -> startsAfter(chip, now))
                    .limit(MAX_CHIPS)
                    .toList();
            if (!chips.isEmpty()) out.add(m.withShowtimes(chips));
        }
        return out;
    }

    private static boolean startsAfter(ShowtimeChip chip, LocalDateTime now) {
        if (chip.showDate().isEmpty()) return true;
        LocalTime time = chip.time().isEmpty() ? LocalTime.MIDNIGHT : LocalTime.parse(chip.time());
        return LocalDateTime.of(LocalDate.parse(chip.showDate()), time).isAfter(now);
    }

    private List<MovieBrowseResponse> comingSoon(YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        List<Movie> movies = movieRepository.findComingSoonBetween(start, end);
        return movies.stream()
                .filter(Objects::nonNull)
                .map(m -> toResponse(m, List.of()))
                .toList();
    }

    private String normalizeCity(String city) {
        return (city == null) ? "" : city.trim();
    }

    private MovieBrowseResponse toResponse(Movie m, List<ShowtimeChip> showtimes) {
        String poster = (m.getImagePath() == null) ? "" : m.getImagePath();
        String banner = (m.getBannerPath() == null) ? "" : m.getBannerPath();
        String trailer = (m.getTrailerUrl() == null) ? "" : m.getTrailerUrl();
        String rating = (m.getRating() == null) ? "" : m.getRating();
        String language = (m.getLanguage() == null) ? "" : m.getLanguage();
        String format = (m.getFormat() == null) ? "" : m.getFormat();
        String releaseDate = (m.getReleaseDate() == null) ? "" : m.getReleaseDate().toString();
        String genre = (m.getGenre() == null) ? "" : m.getGenre();

        return new MovieBrowseResponse(
                m.getId(),
                m.getTitle(),
                poster,
                banner,
                trailer,
                rating,
                language,
                format,
                (m.getStatus() == null ? MovieStatus.NOW_SHOWING.name() : m.getStatus().name()),
                releaseDate,
                showtimes,
                genre,
                m.getDurationMinutes()
        );
    }

    private record MovieGroup(Movie movie, List<ShowtimeChip> showtimes) {
    }

    public record ShowtimeChip(Long showtimeId,
                               String time,
                               String showDate,
                               String hallName,
                               String cinemaName,
                               boolean hasDolbyAtmos,
                               boolean hasScreenX) {
    }

    public record MovieBrowseResponse(Long id,
                                     String title,
                                     String posterPath,
                                     String bannerPath,
                                     String trailerUrl,
                                     String rating,
                                     String language,
                                     String format,
                                     String status,
                                     String releaseDate,
                                     List<ShowtimeChip> showtimes,
                                     String genre,
                                     Integer durationMinutes) {

        MovieBrowseResponse withShowtimes(List<ShowtimeChip> chips) {
            return new MovieBrowseResponse(id, title, posterPath, bannerPath, trailerUrl, rating, language,
                    format, status, releaseDate, chips, genre, durationMinutes);
        }
    }
}
