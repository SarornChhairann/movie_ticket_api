package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.FoodItem;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.PromoCode;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.BookingFoodItemRepository;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.service.BookingService;
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
import java.util.List;
import java.util.Map;

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

    // ---- Halls ----

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

    // ---- Showtimes ----

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

    @PostMapping("/showtimes/{id}/block")
    public String toggleBlockShowtime(@PathVariable Long id, RedirectAttributes ra) {
        showtimeService.toggleBlocked(id);
        ra.addFlashAttribute("success", "Showtime status updated.");
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
    public String saveFoodItem(@ModelAttribute FoodItem foodItem) {
        foodItemService.save(foodItem);
        return "redirect:/admin/food-items";
    }

    @GetMapping("/food-items/delete/{id}")
    public String deleteFoodItem(@PathVariable Long id) {
        foodItemService.delete(id);
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
