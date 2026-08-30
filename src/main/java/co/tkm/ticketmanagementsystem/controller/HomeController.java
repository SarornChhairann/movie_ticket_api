package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Cinema;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.service.CinemaService;
import co.tkm.ticketmanagementsystem.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Home", description = "Landing page data: featured/now-showing/coming-soon movies and cinemas")
public class HomeController {

    private final MovieService movieService;
    private final CinemaService cinemaService;

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get home page data (hero movies, now showing, coming soon, cinemas)")
    public ResponseEntity<HomeResponse> home() {
        List<Movie> nowShowing = movieService.findByStatus(MovieStatus.NOW_SHOWING);
        List<Movie> comingSoon = movieService.findComingSoon();
        List<Movie> heroMovies = buildHeroMovies(nowShowing, comingSoon);
        List<Cinema> cinemas = cinemaService.findAll();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Phnom_Penh"));
        Movie featuredMovie = movieService.findFeaturedMovie().orElse(null);

        return ResponseEntity.ok(new HomeResponse(nowShowing, comingSoon, heroMovies, cinemas, today, featuredMovie));
    }

    private List<Movie> buildHeroMovies(List<Movie> nowShowing, List<Movie> comingSoon) {
        LinkedHashSet<Movie> set = new LinkedHashSet<>();
        movieService.findFeaturedMovie().ifPresent(set::add);
        if (nowShowing != null) {
            for (Movie m : nowShowing) {
                if (set.size() >= 4) break;
                set.add(m);
            }
        }
        if (comingSoon != null) {
            for (Movie m : comingSoon) {
                if (set.size() >= 5) break;
                set.add(m);
            }
        }
        return new ArrayList<>(set);
    }

    public record HomeResponse(List<Movie> nowShowing,
                                List<Movie> comingSoon,
                                List<Movie> heroMovies,
                                List<Cinema> cinemas,
                                LocalDate today,
                                Movie featuredMovie) {
    }
}
