package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.service.BookingService;
import co.tkm.ticketmanagementsystem.service.HallService;
import co.tkm.ticketmanagementsystem.service.MovieService;
import co.tkm.ticketmanagementsystem.service.ShowtimeService;
import co.tkm.ticketmanagementsystem.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final MovieService movieService;
    private final HallService hallService;
    private final ShowtimeService showtimeService;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final UserService userService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("movieCount", movieService.count());
        model.addAttribute("hallCount", hallService.count());
        model.addAttribute("showtimeCount", showtimeService.count());
        model.addAttribute("bookingCount", bookingRepository.count());
        model.addAttribute("userCount", userService.findAll().size());
        return "admin/dashboard";
    }

    @GetMapping("/movies")
    public String movies(Model model) {
        model.addAttribute("movies", movieService.findAll());
        return "admin/movie-list";
    }

    @GetMapping("/movies/new")
    public String newMovie(Model model) {
        model.addAttribute("movie", new Movie());
        return "admin/movie-form";
    }

    @PostMapping("/movies/save")
    public String saveMovie(@ModelAttribute Movie movie,
                            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                            Model model) {
        try {
            movieService.saveWithImage(movie, imageFile);
            return "redirect:/admin/movies";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("movie", movie);
            model.addAttribute("uploadError", ex.getMessage());
            return "admin/movie-form";
        } catch (Exception ex) {
            model.addAttribute("movie", movie);
            model.addAttribute("uploadError", "Unable to upload image. Please try again.");
            return "admin/movie-form";
        }
    }

    @GetMapping("/movies/edit/{id}")
    public String editMovie(@PathVariable Long id, Model model) {
        model.addAttribute("movie", movieService.findById(id));
        return "admin/movie-form";
    }

    @GetMapping("/movies/delete/{id}")
    public String deleteMovie(@PathVariable Long id) {
        movieService.delete(id);
        return "redirect:/admin/movies";
    }

    @GetMapping("/halls")
    public String halls(Model model) {
        model.addAttribute("halls", hallService.findAll());
        return "admin/hall-list";
    }

    @GetMapping("/halls/new")
    public String newHall(Model model) {
        model.addAttribute("hall", new Hall());
        return "admin/hall-form";
    }

    @PostMapping("/halls/save")
    public String saveHall(@ModelAttribute Hall hall) {
        hallService.save(hall);
        return "redirect:/admin/halls";
    }

    @GetMapping("/halls/edit/{id}")
    public String editHall(@PathVariable Long id, Model model) {
        model.addAttribute("hall", hallService.findById(id));
        return "admin/hall-form";
    }

    @GetMapping("/halls/delete/{id}")
    public String deleteHall(@PathVariable Long id) {
        hallService.deleteById(id);
        return "redirect:/admin/halls";
    }

    @GetMapping("/showtimes")
    public String showtimes(Model model) {
        model.addAttribute("showtimes", showtimeService.findAll());
        return "admin/showtime-list";
    }

    @GetMapping("/showtimes/new")
    public String newShowtime(Model model) {
        model.addAttribute("showtime", new Showtime());
        model.addAttribute("movies", movieService.findAll());
        model.addAttribute("halls", hallService.findAll());
        return "admin/showtime-form";
    }

    @PostMapping("/showtimes/save")
    public String saveShowtime(@ModelAttribute Showtime showtime) {
        showtime.setMovie(movieService.findById(showtime.getMovie().getId()));
        showtime.setHall(hallService.findById(showtime.getHall().getId()));
        showtimeService.save(showtime);
        return "redirect:/admin/showtimes";
    }

    @GetMapping("/showtimes/delete/{id}")
    public String deleteShowtime(@PathVariable Long id) {
        showtimeService.deleteById(id);
        return "redirect:/admin/showtimes";
    }

    @GetMapping("/bookings")
    public String bookings(Model model) {
        var bookings = bookingService.getAllBookingsForAdmin();
        model.addAttribute("bookings", bookings);
        return "admin/booking-list";
    }

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userService.findAll());
        return "admin/user-list";
    }
}
