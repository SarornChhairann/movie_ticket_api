package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.model.Cinema;
import co.tkm.ticketmanagementsystem.model.FoodItem;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.PromoCode;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.BookingFoodItemRepository;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.service.BookingService;
import co.tkm.ticketmanagementsystem.service.CinemaService;
import co.tkm.ticketmanagementsystem.service.FoodItemService;
import co.tkm.ticketmanagementsystem.service.HallService;
import co.tkm.ticketmanagementsystem.service.MovieService;
import co.tkm.ticketmanagementsystem.service.NotificationService;
import co.tkm.ticketmanagementsystem.service.PromoCodeService;
import co.tkm.ticketmanagementsystem.service.ReportService;
import co.tkm.ticketmanagementsystem.service.ShowtimeService;
import co.tkm.ticketmanagementsystem.service.SystemSettingService;
import co.tkm.ticketmanagementsystem.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final MovieService movieService;
    private final HallService hallService;
    private final CinemaService cinemaService;
    private final ShowtimeService showtimeService;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final UserService userService;
    private final FoodItemService foodItemService;
    private final PromoCodeService promoCodeService;
    private final ReportService reportService;
    private final NotificationService notificationService;
    private final SystemSettingService systemSettingService;
    private final BookingFoodItemRepository bookingFoodItemRepository;

    // ---- Dashboard ----

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("movieCount", movieService.count());
        model.addAttribute("hallCount", hallService.count());
        model.addAttribute("showtimeCount", showtimeService.count());
        model.addAttribute("bookingCount", bookingRepository.count());
        model.addAttribute("userCount", userService.findAll().size());
        model.addAttribute("revenueToday", reportService.getRevenueToday());
        model.addAttribute("revenueWeek", reportService.getRevenueThisWeek());
        model.addAttribute("revenueMonth", reportService.getRevenueThisMonth());
        model.addAttribute("bookingsToday", reportService.getBookingsToday());
        model.addAttribute("topMovies", reportService.getTopMovies(5));

        List<Map<String, Object>> dailyStats = reportService.getDailyStats(14);
        StringBuilder labels = new StringBuilder("[");
        StringBuilder counts = new StringBuilder("[");
        StringBuilder revenues = new StringBuilder("[");
        for (int i = 0; i < dailyStats.size(); i++) {
            Map<String, Object> d = dailyStats.get(i);
            if (i > 0) { labels.append(","); counts.append(","); revenues.append(","); }
            labels.append("\"").append(d.get("date")).append("\"");
            counts.append(d.get("count"));
            revenues.append(d.get("revenue"));
        }
        labels.append("]"); counts.append("]"); revenues.append("]");
        model.addAttribute("chartLabels", labels.toString());
        model.addAttribute("chartCounts", counts.toString());
        model.addAttribute("chartRevenues", revenues.toString());
        model.addAttribute("recentBookings", bookingService.getAllBookingsForAdmin().stream().limit(5).toList());
        return "admin/dashboard";
    }

    // ---- Movies ----

    @GetMapping("/movies")
    public String movies(Model model) {
        model.addAttribute("movies", movieService.findAll());
        return "admin/movie-list";
    }

    @GetMapping("/movies/new")
    public String newMovie(Model model) {
        Movie movie = new Movie();
        movie.setStatus(MovieStatus.COMING_SOON);
        model.addAttribute("movie", movie);
        return "admin/movie-form";
    }

    @PostMapping("/movies/save")
    public String saveMovie(@ModelAttribute Movie movie,
                            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                            @RequestParam(value = "bannerFile", required = false) MultipartFile bannerFile,
                            Model model) {
        try {
            movieService.saveWithImages(movie, imageFile, bannerFile);
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

    // ---- Halls ----

    @GetMapping("/halls")
    public String halls(Model model) {
        model.addAttribute("halls", hallService.findAll());
        return "admin/hall-list";
    }

    @GetMapping("/halls/new")
    public String newHall(Model model) {
        Hall hall = new Hall();
        hall.setCinema(new Cinema());
        model.addAttribute("hall", hall);
        model.addAttribute("cinemas", cinemaService.findAll());
        return "admin/hall-form";
    }

    @PostMapping("/halls/save")
    public String saveHall(@ModelAttribute Hall hall, Model model, RedirectAttributes ra) {
        try {
            hallService.save(hall);
            ra.addFlashAttribute("success", "Hall saved.");
            return "redirect:/admin/halls";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("hall", hall);
            model.addAttribute("cinemas", cinemaService.findAll());
            model.addAttribute("error", ex.getMessage());
            return "admin/hall-form";
        }
    }

    @GetMapping("/halls/edit/{id}")
    public String editHall(@PathVariable Long id, Model model) {
        Hall hall = hallService.findById(id);
        if (hall.getCinema() == null) {
            hall.setCinema(new Cinema());
        }
        model.addAttribute("hall", hall);
        model.addAttribute("cinemas", cinemaService.findAll());
        return "admin/hall-form";
    }

    @GetMapping("/halls/delete/{id}")
    public String deleteHall(@PathVariable Long id) {
        hallService.deleteById(id);
        return "redirect:/admin/halls";
    }

    // ---- Cinemas ----

    @GetMapping("/cinemas")
    public String cinemas(Model model) {
        model.addAttribute("cinemas", cinemaService.findAll());
        return "admin/cinema-list";
    }

    @GetMapping("/cinemas/new")
    public String newCinema(Model model) {
        model.addAttribute("cinema", new Cinema());
        return "admin/cinema-form";
    }

    @PostMapping("/cinemas/save")
    public String saveCinema(@ModelAttribute Cinema cinema, Model model, RedirectAttributes ra) {
        try {
            cinemaService.save(cinema);
            ra.addFlashAttribute("success", "Cinema saved.");
            return "redirect:/admin/cinemas";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("cinema", cinema);
            model.addAttribute("error", ex.getMessage());
            return "admin/cinema-form";
        }
    }

    @GetMapping("/cinemas/edit/{id}")
    public String editCinema(@PathVariable UUID id, Model model) {
        model.addAttribute("cinema", cinemaService.findById(id));
        return "admin/cinema-form";
    }

    @GetMapping("/cinemas/delete/{id}")
    public String deleteCinema(@PathVariable UUID id, RedirectAttributes ra) {
        long halls = hallService.countByCinemaId(id);
        if (halls > 0) {
            ra.addFlashAttribute("error", "Cannot delete cinema: it has " + halls + " hall(s). Delete or move halls first.");
            return "redirect:/admin/cinemas";
        }
        cinemaService.deleteById(id);
        ra.addFlashAttribute("success", "Cinema deleted.");
        return "redirect:/admin/cinemas";
    }

    // ---- Showtimes ----

    @GetMapping("/showtimes")
    public String showtimes(Model model) {
        List<Showtime> showtimes = showtimeService.findAll();
        model.addAttribute("showtimes", showtimes);

        List<Long> ids = showtimes.stream().map(Showtime::getId).filter(Objects::nonNull).toList();
        Map<Long, Long> bookingCounts = new HashMap<>();
        Map<Long, Long> activeBookingCounts = new HashMap<>();
        for (Long id : ids) {
            long c = bookingRepository.countByShowtimeId(id);
            if (c > 0) bookingCounts.put(id, c);
            long a = bookingRepository.countByShowtimeIdAndStatusNot(id, BookingStatus.CANCELLED);
            if (a > 0) activeBookingCounts.put(id, a);
        }
        model.addAttribute("bookingCounts", bookingCounts);
        model.addAttribute("activeBookingCounts", activeBookingCounts);

        int editLockHours = systemSettingService.getInt(SystemSettingService.CANCELLATION_HOURS, 2);
        ZoneId zone = ZoneId.of("Asia/Phnom_Penh");
        LocalDateTime lockCutoff = LocalDateTime.now(zone).plusHours(Math.max(0, editLockHours));
        Map<Long, Boolean> lockedEdits = new HashMap<>();
        for (Showtime s : showtimes) {
            if (s.getId() == null || s.getShowDate() == null || s.getShowTime() == null) continue;
            LocalDateTime start = LocalDateTime.of(s.getShowDate(), s.getShowTime());
            lockedEdits.put(s.getId(), !start.isAfter(lockCutoff));
        }
        model.addAttribute("editLockHours", editLockHours);
        model.addAttribute("lockedEdits", lockedEdits);
        return "admin/showtime-list";
    }

    @GetMapping("/showtimes/new")
    public String newShowtime(Model model) {
        model.addAttribute("showtime", new Showtime());
        model.addAttribute("movies", movieService.findAll());
        model.addAttribute("halls", hallService.findAll());
        return "admin/showtime-form";
    }

    @GetMapping("/showtimes/edit/{id}")
    public String editShowtime(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Showtime showtime = showtimeService.findById(id);
        long bookings = bookingRepository.countByShowtimeId(id);
        if (bookings > 0) {
            ra.addFlashAttribute("error", "Cannot edit showtime because bookings already exist.");
            return "redirect:/admin/showtimes";
        }
        if (showtimeService.isWithinEditLockWindow(showtime)) {
            int hours = systemSettingService.getInt(SystemSettingService.CANCELLATION_HOURS, 2);
            ra.addFlashAttribute("error", "Cannot edit showtime within " + hours + " hour(s) of start time.");
            return "redirect:/admin/showtimes";
        }

        model.addAttribute("showtime", showtime);
        model.addAttribute("movies", movieService.findAll());
        model.addAttribute("halls", hallService.findAll());
        model.addAttribute("publishNow", !showtime.isBlocked());
        return "admin/showtime-form";
    }

    @PostMapping("/showtimes/save")
    public String saveShowtime(@ModelAttribute Showtime showtime,
                               @RequestParam(value = "publishNow", defaultValue = "true") boolean publishNow,
                               @RequestParam(value = "sendNotification", defaultValue = "false") boolean sendNotification,
                               Model model,
                               RedirectAttributes ra) {
        try {
            showtime.setMovie(movieService.findById(showtime.getMovie().getId()));
            showtime.setHall(hallService.findById(showtime.getHall().getId()));
            showtime.setBlocked(!publishNow);
            Showtime saved = showtimeService.save(showtime);

            if (publishNow && sendNotification) {
                notificationService.sendBroadcast(
                        "New movie available!",
                        saved.getMovie().getTitle() + " is now available. " +
                        "Showtime: " + saved.getShowDate() + " " + saved.getShowTime() +
                        " at " + saved.getHall().getName() + "."
                );
            }

            ra.addFlashAttribute("success", publishNow ? "Showtime published." : "Showtime saved as draft (blocked).");
            return "redirect:/admin/showtimes";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("showtime", showtime);
            model.addAttribute("movies", movieService.findAll());
            model.addAttribute("halls", hallService.findAll());
            model.addAttribute("publishNow", publishNow);
            model.addAttribute("sendNotification", sendNotification);
            return "admin/showtime-form";
        }
    }

    @GetMapping("/showtimes/delete/{id}")
    public String deleteShowtime(@PathVariable Long id, RedirectAttributes ra) {
        try {
            showtimeService.deleteById(id);
            ra.addFlashAttribute("success", "Showtime deleted.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Unable to delete showtime. Please try again.");
        }
        return "redirect:/admin/showtimes";
    }

    @PostMapping("/showtimes/{id}/block")
    public String toggleBlockShowtime(@PathVariable Long id, RedirectAttributes ra) {
        try {
            showtimeService.toggleBlocked(id);
            ra.addFlashAttribute("success", "Showtime status updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Unable to update showtime status. Please try again.");
        }
        return "redirect:/admin/showtimes";
    }

    @PostMapping("/showtimes/{id}/copy-next-week")
    public String copyShowtimeNextWeek(@PathVariable Long id, RedirectAttributes ra) {
        showtimeService.copyToNextWeek(id);
        ra.addFlashAttribute("success", "Showtime copied to next week.");
        return "redirect:/admin/showtimes";
    }

    // ---- Bookings ----

    @GetMapping("/bookings")
    public String bookings(@RequestParam(required = false) String q, Model model) {
        var bookings = (q != null && !q.isBlank())
                ? bookingService.searchBookings(q)
                : bookingService.getAllBookingsForAdmin();
        model.addAttribute("bookings", bookings);
        model.addAttribute("q", q);
        return "admin/booking-list";
    }

    @PostMapping("/bookings/{id}/cancel")
    public String adminCancelBooking(@PathVariable Long id, RedirectAttributes ra) {
        try {
            bookingService.adminCancelBooking(id);
            ra.addFlashAttribute("success", "Booking #" + id + " cancelled.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/bookings";
    }

    @GetMapping("/bookings/export")
    public ResponseEntity<byte[]> exportBookingsCsv(@RequestParam(required = false) String q) {
        byte[] csv = reportService.generateBookingsCsv(q);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bookings.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    // ---- Users ----

    @GetMapping("/users")
    public String users(@RequestParam(required = false) String q, Model model) {
        var users = (q != null && !q.isBlank())
                ? userService.search(q)
                : userService.findAll();
        model.addAttribute("users", users);
        model.addAttribute("q", q);
        return "admin/user-list";
    }

    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable Long id, Model model) {
        var user = userService.findById(id);
        var bookings = bookingService.getBookingsByUser(user);
        model.addAttribute("user", user);
        model.addAttribute("bookings", bookings);
        model.addAttribute("statuses", UserStatus.values());
        return "admin/user-detail";
    }

    @PostMapping("/users/{id}/status")
    public String setUserStatus(@PathVariable Long id,
                                @RequestParam UserStatus status,
                                RedirectAttributes ra) {
        userService.setStatus(id, status);
        ra.addFlashAttribute("success", "User status updated to " + status + ".");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/points")
    public String adjustUserPoints(@PathVariable Long id,
                                   @RequestParam int delta,
                                   RedirectAttributes ra) {
        userService.adjustPoints(id, delta);
        ra.addFlashAttribute("success", "Points adjusted by " + (delta >= 0 ? "+" : "") + delta + ".");
        return "redirect:/admin/users/" + id;
    }

    // ---- Food Items ----

    @GetMapping("/food-items")
    public String foodItems(Model model) {
        model.addAttribute("foodItems", foodItemService.findAll());
        return "admin/food-items";
    }

    @GetMapping("/food-items/new")
    public String newFoodItem(Model model) {
        model.addAttribute("foodItem", new FoodItem());
        return "admin/food-item-form";
    }

    @GetMapping("/food-items/edit/{id}")
    public String editFoodItem(@PathVariable Long id, Model model) {
        model.addAttribute("foodItem", foodItemService.findById(id));
        return "admin/food-item-form";
    }

    @PostMapping("/food-items/save")
    public String saveFoodItem(@ModelAttribute FoodItem foodItem,
                               @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                               Model model,
                               RedirectAttributes ra) {
        try {
            foodItemService.saveWithImage(foodItem, imageFile);
            ra.addFlashAttribute("success", "Food item saved.");
            return "redirect:/admin/food-items";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("foodItem", foodItem);
            return "admin/food-item-form";
        }
    }

    @GetMapping("/food-items/delete/{id}")
    public String deleteFoodItem(@PathVariable Long id, RedirectAttributes ra) {
        foodItemService.delete(id);
        ra.addFlashAttribute("success", "Food item deleted.");
        return "redirect:/admin/food-items";
    }

    // ---- Food Orders ----

    @GetMapping("/food-orders")
    public String foodOrders(@RequestParam(required = false) String date, Model model) {
        LocalDate targetDate = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        var orders = bookingFoodItemRepository.findByShowtimeDate(targetDate);
        model.addAttribute("orders", orders);
        model.addAttribute("date", targetDate);
        return "admin/food-orders";
    }

    // ---- Promo Codes ----

    @GetMapping("/promo-codes")
    public String promoCodes(Model model) {
        model.addAttribute("promoCodes", promoCodeService.findAll());
        return "admin/promo-codes";
    }

    @GetMapping("/promo-codes/new")
    public String newPromoCode(Model model) {
        model.addAttribute("promoCode", new PromoCode());
        return "admin/promo-code-form";
    }

    @GetMapping("/promo-codes/edit/{id}")
    public String editPromoCode(@PathVariable Long id, Model model) {
        model.addAttribute("promoCode", promoCodeService.findById(id));
        return "admin/promo-code-form";
    }

    @PostMapping("/promo-codes/save")
    public String savePromoCode(@ModelAttribute PromoCode promoCode) {
        promoCodeService.save(promoCode);
        return "redirect:/admin/promo-codes";
    }

    @GetMapping("/promo-codes/delete/{id}")
    public String deletePromoCode(@PathVariable Long id) {
        promoCodeService.delete(id);
        return "redirect:/admin/promo-codes";
    }

    // ---- Reports ----

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("revenueToday", reportService.getRevenueToday());
        model.addAttribute("revenueWeek", reportService.getRevenueThisWeek());
        model.addAttribute("revenueMonth", reportService.getRevenueThisMonth());
        model.addAttribute("topMovies", reportService.getTopMovies(10));

        List<Map<String, Object>> dailyStats = reportService.getDailyStats(30);
        StringBuilder labels = new StringBuilder("[");
        StringBuilder revenues = new StringBuilder("[");
        for (int i = 0; i < dailyStats.size(); i++) {
            Map<String, Object> d = dailyStats.get(i);
            if (i > 0) { labels.append(","); revenues.append(","); }
            labels.append("\"").append(d.get("date")).append("\"");
            revenues.append(d.get("revenue"));
        }
        labels.append("]"); revenues.append("]");
        model.addAttribute("chartLabels", labels.toString());
        model.addAttribute("chartRevenues", revenues.toString());
        return "admin/reports";
    }

    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> exportReportCsv(@RequestParam(required = false) String q) {
        byte[] csv = reportService.generateBookingsCsv(q);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    // ---- Notifications ----

    @GetMapping("/notifications")
    public String notifications(Model model) {
        model.addAttribute("notifications", notificationService.findAll());
        return "admin/notifications";
    }

    @PostMapping("/notifications/send")
    public String sendNotification(@RequestParam String title,
                                   @RequestParam String message,
                                   RedirectAttributes ra) {
        notificationService.sendBroadcast(title, message);
        ra.addFlashAttribute("success", "Notification sent to all users.");
        return "redirect:/admin/notifications";
    }

    @PostMapping("/notifications/{id}/delete")
    public String deleteNotification(@PathVariable Long id, RedirectAttributes ra) {
        notificationService.delete(id);
        ra.addFlashAttribute("success", "Notification deleted.");
        return "redirect:/admin/notifications";
    }

    // ---- System Settings ----

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("settings", systemSettingService.findAll());
        return "admin/settings";
    }

    @PostMapping("/settings/save")
    public String saveSettings(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        params.forEach((key, value) -> {
            if (!key.startsWith("_") && !key.equals("submit")) {
                systemSettingService.set(key, value);
            }
        });
        ra.addFlashAttribute("success", "Settings saved.");
        return "redirect:/admin/settings";
    }
}
