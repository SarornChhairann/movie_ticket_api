package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.service.HallService;
import co.tkm.ticketmanagementsystem.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final MovieService movieService;
    private final HallService hallService;

    @GetMapping("/")
    public String home(Model model) {
        List<Movie> nowShowing = movieService.findByStatus(MovieStatus.NOW_SHOWING);
        List<Movie> comingSoon = movieService.findComingSoon();
        List<String> cities = hallService.findDistinctCities();

        model.addAttribute("nowShowing", nowShowing);
        model.addAttribute("comingSoon", comingSoon);
        model.addAttribute("cities", cities);
        model.addAttribute("today", LocalDate.now());
        movieService.findFeaturedMovie().ifPresent(m -> model.addAttribute("featuredMovie", m));
        return "home";
    }
}

