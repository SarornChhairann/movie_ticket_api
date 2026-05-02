package co.tkm.ticketmanagementsystem.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpSession;
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

    @GetMapping("/seats/{showtimeId}")
    public String seatMap(@PathVariable Long showtimeId, HttpSession session, Model model) {
        List<ShowtimeSeat> showtimeSeats = bookingService.getSeatsForShowtime(showtimeId);
        Set<Long> heldByOthers = seatHoldService.getHeldSeatIdsByOtherSessions(session.getId());
        model.addAttribute("showtimeId", showtimeId);
        model.addAttribute("showtimeSeats", showtimeSeats);
        model.addAttribute("heldByOthers", heldByOthers);
        return "booking/seat-map";
    }

    @GetMapping("/checkout/{showtimeId}")
    public String checkoutPreview(@PathVariable Long showtimeId,
                                  @RequestParam("seatIds") List<Long> seatIds,
                                  Authentication authentication,
                                  HttpSession session,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        String userEmail = resolveEmail(authentication);
        try {
            CheckoutData checkoutData = bookingService.buildCheckout(showtimeId, seatIds, userEmail);
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

    @PostMapping("/checkout/{showtimeId}")
    public String checkout(@PathVariable Long showtimeId,
                           @RequestParam("seatIds") List<Long> seatIds,
                           @RequestParam(value = "guestName", required = false) String guestName,
                           @RequestParam(value = "guestPhone", required = false) String guestPhone,
                           @RequestParam(value = "guestEmail", required = false) String guestEmail,
                           Authentication authentication,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        String userEmail = resolveEmail(authentication);
        try {
            if (seatHoldService.hasActiveConflict(seatIds, session.getId())) {
                redirectAttributes.addFlashAttribute("error", "Some seats were selected by another user. Please reselect.");
                return "redirect:/bookings/seats/" + showtimeId;
            }
            Booking booking = bookingService.confirmCheckout(showtimeId, seatIds, userEmail, guestName, guestPhone, guestEmail);
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
