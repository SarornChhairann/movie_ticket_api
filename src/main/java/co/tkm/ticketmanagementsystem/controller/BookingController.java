package co.tkm.ticketmanagementsystem.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.tkm.ticketmanagementsystem.dto.CheckoutData;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.service.BookingService;
import co.tkm.ticketmanagementsystem.service.TicketService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final TicketService ticketService;

    @GetMapping("/seats/{showtimeId}")
    public String seatMap(@PathVariable Long showtimeId, Model model) {
        List<ShowtimeSeat> showtimeSeats = bookingService.getSeatsForShowtime(showtimeId);
        model.addAttribute("showtimeId", showtimeId);
        model.addAttribute("showtimeSeats", showtimeSeats);
        return "booking/seat-map";
    }

    @GetMapping("/checkout/{showtimeId}")
    public String checkoutPreview(@PathVariable Long showtimeId,
                                  @RequestParam("seatIds") List<Long> seatIds,
                                  Authentication authentication,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        String userEmail = resolveEmail(authentication);
        try {
            CheckoutData checkoutData = bookingService.buildCheckout(showtimeId, seatIds, userEmail);
            model.addAttribute("checkoutData", checkoutData);
            model.addAttribute("seatIds", seatIds);
            model.addAttribute("isAuthenticated", authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal()));
            return "booking/checkout";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/bookings/seats/" + showtimeId;
        }
    }

    @PostMapping("/checkout/{showtimeId}")
    public String checkout(@PathVariable Long showtimeId,
                           @RequestParam("seatIds") List<Long> seatIds,
                           @RequestParam(value = "guestName", required = false) String guestName,
                           @RequestParam(value = "guestPhone", required = false) String guestPhone,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        String userEmail = resolveEmail(authentication);
        try {
            Booking booking = bookingService.confirmCheckout(showtimeId, seatIds, userEmail, guestName, guestPhone);
            return "redirect:/bookings/confirmation/" + booking.getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/bookings/seats/" + showtimeId;
        }
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
