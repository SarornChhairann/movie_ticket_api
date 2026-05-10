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
import java.time.YearMonth;
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
            return ResponseEntity.ok(cacheService.getOrLoad(key, () -> nowShowing(date, cinemaId, c)));
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
            g.showtimes.add(new ShowtimeChip(s.getId(), s.getShowTime() != null ? TIME_FMT.format(s.getShowTime()) : ""));
        }

        List<MovieBrowseResponse> out = new ArrayList<>();
        for (MovieGroup g : grouped.values()) {
            g.showtimes.sort(Comparator.comparing(ShowtimeChip::time));
            List<ShowtimeChip> chips = g.showtimes.size() > 4 ? g.showtimes.subList(0, 4) : g.showtimes;
            out.add(toResponse(g.movie, chips));
        }
        return out;
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
                showtimes
        );
    }

    private record MovieGroup(Movie movie, List<ShowtimeChip> showtimes) {
    }

    public record ShowtimeChip(Long showtimeId, String time) {
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
                                     List<ShowtimeChip> showtimes) {
    }
}
