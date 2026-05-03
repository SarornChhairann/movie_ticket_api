package co.tkm.ticketmanagementsystem.controller;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.tkm.ticketmanagementsystem.dto.CheckoutData;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.service.BookingService;
import co.tkm.ticketmanagementsystem.service.PromoCodeService;
import co.tkm.ticketmanagementsystem.service.SeatHoldService;
import co.tkm.ticketmanagementsystem.service.TicketService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final TicketService ticketService;
    private final SeatHoldService seatHoldService;
    private final PromoCodeService promoCodeService;

    @GetMapping("/seats/{showtimeId}")
    public String seatMap(@PathVariable Long showtimeId, HttpSession session, Model model) {
        List<ShowtimeSeat> showtimeSeats = bookingService.getSeatsForShowtime(showtimeId);
        Set<Long> heldByOthers = seatHoldService.getHeldSeatIdsByOtherSessions(session.getId());
        co.tkm.ticketmanagementsystem.model.Showtime showtime = bookingService.getShowtime(showtimeId);
        DayOfWeek dow = showtime.getShowDate().getDayOfWeek();
        boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        boolean isMorning = showtime.getShowTime() != null
                && showtime.getShowTime().isBefore(LocalTime.NOON);
        model.addAttribute("showtimeId", showtimeId);
        model.addAttribute("showtime", showtime);
        model.addAttribute("showtimeSeats", showtimeSeats);
        model.addAttribute("heldByOthers", heldByOthers);
        model.addAttribute("holdMinutes", seatHoldService.getHoldMinutes());
        model.addAttribute("isWeekend", isWeekend);
        model.addAttribute("isMorning", isMorning);
        return "booking/seat-map";
    }

    @GetMapping("/checkout/{showtimeId}")
    public String checkoutPreview(@PathVariable Long showtimeId,
                                  @RequestParam("seatIds") List<Long> seatIds,
                                  @RequestParam(value = "promoCode", required = false) String promoCode,
                                  Authentication authentication,
                                  HttpSession session,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        String userEmail = resolveEmail(authentication);
        try {
            CheckoutData checkoutData = bookingService.buildCheckout(showtimeId, seatIds, userEmail, promoCode);
            seatHoldService.holdSeats(seatIds, session.getId());
            model.addAttribute("checkoutData", checkoutData);
            model.addAttribute("seatIds", seatIds);
            model.addAttribute("holdMinutes", seatHoldService.getHoldMinutes());
            model.addAttribute("isAuthenticated", authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal()));
            return "booking/checkout";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/bookings/seats/" + showtimeId;
        }
    }

    @PostMapping("/promo/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validatePromo(@RequestParam("code") String code) {
        Map<String, Object> result = new HashMap<>();
        try {
            int pct = promoCodeService.validate(code);
            result.put("valid", true);
            result.put("discountPercent", pct);
            result.put("message", pct + "% discount applied!");
        } catch (IllegalArgumentException ex) {
            result.put("valid", false);
            result.put("message", ex.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/checkout/{showtimeId}")
    public String checkout(@PathVariable Long showtimeId,
                           @RequestParam("seatIds") List<Long> seatIds,
                           @RequestParam(value = "guestName", required = false) String guestName,
                           @RequestParam(value = "guestPhone", required = false) String guestPhone,
                           @RequestParam(value = "guestEmail", required = false) String guestEmail,
                           @RequestParam(value = "promoCode", required = false) String promoCode,
                           @RequestParam(value = "paymentMethod", required = false, defaultValue = "CARD") String paymentMethod,
                           @RequestParam Map<String, String> allParams,
                           Authentication authentication,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        String userEmail = resolveEmail(authentication);

        // Parse food selections: food_{id} -> quantity
        Map<Long, Integer> foodSelections = new HashMap<>();
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (entry.getKey().startsWith("food_")) {
                try {
                    Long foodId = Long.parseLong(entry.getKey().substring(5));
                    int qty = Integer.parseInt(entry.getValue());
                    if (qty > 0) foodSelections.put(foodId, qty);
                } catch (NumberFormatException ignored) {}
            }
        }

        try {
            if (seatHoldService.hasActiveConflict(seatIds, session.getId())) {
                redirectAttributes.addFlashAttribute("error", "Some seats were selected by another user. Please reselect.");
                return "redirect:/bookings/seats/" + showtimeId;
            }
            Booking booking = bookingService.confirmCheckout(
                    showtimeId, seatIds, userEmail,
                    guestName, guestPhone, guestEmail,
                    promoCode, paymentMethod, foodSelections);
            seatHoldService.releaseHolds(session.getId());
            return "redirect:/bookings/confirmation/" + booking.getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/bookings/seats/" + showtimeId;
        }
    }

    @PostMapping("/cancel/{bookingId}")
    public String cancelBooking(@PathVariable Long bookingId,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            bookingService.cancelBooking(bookingId, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Booking #" + bookingId + " has been cancelled.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/bookings/my-bookings";
    }

    @GetMapping("/confirmation/{bookingId}")
    public String confirmation(@PathVariable Long bookingId, Model model) {
        Booking booking = bookingService.findById(bookingId);
        model.addAttribute("booking", booking);
        List<Ticket> tickets = ticketService.findByBooking(booking);
        model.addAttribute("tickets", tickets);
        return "booking/confirmation";
    }

    @GetMapping("/my-bookings")
    public String myBookings(Authentication authentication, Model model) {
        List<Booking> bookings = bookingService.getBookingsForUser(authentication.getName());
        Map<Long, String> firstTicketByBooking = new LinkedHashMap<>();
        for (Booking booking : bookings) {
            ticketService.findByBooking(booking).stream().findFirst()
                    .ifPresent(ticket -> firstTicketByBooking.put(booking.getId(), ticket.getTicketCode()));
        }
        model.addAttribute("bookings", bookings);
        model.addAttribute("firstTicketByBooking", firstTicketByBooking);
        return "booking/my-bookings";
    }

    private String resolveEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return authentication.getName();
    }
}
