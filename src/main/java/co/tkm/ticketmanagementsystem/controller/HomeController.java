package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.service.CinemaService;
import co.tkm.ticketmanagementsystem.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final MovieService movieService;
    private final CinemaService cinemaService;

    @GetMapping("/")
    public String home(Model model) {
        List<Movie> nowShowing = movieService.findByStatus(MovieStatus.NOW_SHOWING);
        List<Movie> comingSoon = movieService.findComingSoon();

        List<Movie> heroMovies = buildHeroMovies(nowShowing, comingSoon);
        model.addAttribute("nowShowing", nowShowing);
        model.addAttribute("comingSoon", comingSoon);
        model.addAttribute("heroMovies", heroMovies);
        model.addAttribute("cinemas", cinemaService.findAll());
        model.addAttribute("today", LocalDate.now(ZoneId.of("Asia/Phnom_Penh")));
        movieService.findFeaturedMovie().ifPresent(m -> model.addAttribute("featuredMovie", m));
        return "home";
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
}
