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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;
    private final ShowtimeRepository showtimeRepository;
    private final HallService hallService;
    private final BookingService bookingService;

    @GetMapping({"/", "/movies"})
    public String list(
            @RequestParam(value = "tab", required = false, defaultValue = "NOW_SHOWING") String tab,
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "format", required = false) String format,
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

        List<Movie> movies = movieService.filterMovies(status, genre, language, format, search);
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
    public String detail(@PathVariable Long id, Model model) {
        Movie movie = movieService.findById(id);
        List<Showtime> showtimes = showtimeRepository.findByMovie(movie);
        Map<String, List<Showtime>> grouped = showtimes.stream()
                .collect(Collectors.groupingBy(s -> s.getShowDate().toString(), LinkedHashMap::new, Collectors.toList()));

        model.addAttribute("movie", movie);
        model.addAttribute("showtimeByDate", grouped);
        return "movies/detail";
    }
}
