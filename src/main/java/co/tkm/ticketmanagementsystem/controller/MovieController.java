package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.service.BookingService;
import co.tkm.ticketmanagementsystem.service.HallService;
import co.tkm.ticketmanagementsystem.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;
    private final ShowtimeRepository showtimeRepository;
    private final HallService hallService;
    private final BookingService bookingService;

    @GetMapping("/movies")
    public String list(
            @RequestParam(value = "tab", required = false, defaultValue = "NOW_SHOWING") String tab,
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "format", required = false) String format,
            @RequestParam(value = "rating", required = false) String rating,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "city", required = false) String city,
            Authentication authentication,
            Model model) {

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
        movieService.findFeaturedMovie().ifPresent(m -> model.addAttribute("featuredMovie", m));

        model.addAttribute("movies", movies);
        model.addAttribute("comingSoon", comingSoon);
        model.addAttribute("halls", halls);
        model.addAttribute("cities", cities);
        model.addAttribute("selectedCity", city);
        model.addAttribute("tab", status.name());
        model.addAttribute("genre", genre);
        model.addAttribute("language", language);
        model.addAttribute("format", format);
        model.addAttribute("rating", rating);
        model.addAttribute("search", search);
        model.addAttribute("genres", movieService.findDistinctGenres());
        model.addAttribute("languages", movieService.findDistinctLanguages());

        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin) {
                model.addAttribute("userBookingCount", bookingService.countBookingsForUser(authentication.getName()));
            }
        }

        return "movies/list";
    }

    @GetMapping("/movies/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(value = "city", required = false) String city,
                         @RequestParam(value = "cinemaId", required = false) UUID cinemaId,
                         @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam(value = "hallId", required = false) Long hallId,
                         Model model) {
        Movie movie = movieService.findById(id);
        List<Showtime> showtimes = showtimeRepository.findByMovieIdWithHall(id);

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

        Map<LocalDate, List<Showtime>> grouped = filteredShowtimes.stream()
                .collect(Collectors.groupingBy(Showtime::getShowDate, LinkedHashMap::new, Collectors.toList()));

        List<String> cities = hallService.findDistinctCities();

        model.addAttribute("movie", movie);
        model.addAttribute("showtimeByDate", grouped);
        model.addAttribute("cities", cities);
        model.addAttribute("selectedCity", city);
        model.addAttribute("selectedCinemaId", cinemaId);
        model.addAttribute("availableDates", availableDates);
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("availableHalls", availableHalls);
        model.addAttribute("selectedHallId", hallId);
        return "movies/detail";
    }
}
