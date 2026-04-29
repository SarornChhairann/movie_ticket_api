package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;
    private final ShowtimeRepository showtimeRepository;

    @GetMapping({"/", "/movies"})
    public String list(Model model) {
        List<Movie> movies = movieService.findByStatus(MovieStatus.NOW_SHOWING);
        model.addAttribute("movies", movies);
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
