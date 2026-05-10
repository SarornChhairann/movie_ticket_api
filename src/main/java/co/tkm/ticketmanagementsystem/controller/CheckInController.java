package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/checkin")
@RequiredArgsConstructor
public class CheckInController {

    private final TicketService ticketService;

    @GetMapping
    public String checkInPage() {
        return "admin/checkin";
    }

    @PostMapping
    public String processCheckIn(@RequestParam("ticketCode") String ticketCode, Model model) {
        model.addAttribute("globalFlash", false);
        String code = ticketCode == null ? "" : ticketCode.trim();
        if (code.isEmpty()) {
            model.addAttribute("error", "Please enter a ticket code.");
            return "admin/checkin";
        }
        try {
            Ticket ticket = ticketService.checkIn(code);
            model.addAttribute("ticket", ticket);
            model.addAttribute("success", "Check-in successful!");
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("failedCode", code);
        }
        return "admin/checkin";
    }
}
