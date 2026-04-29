package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/search")
    public String searchPage() {
        return "ticket/search";
    }

    @GetMapping
    public String searchByCode(@RequestParam("ticketCode") String ticketCode,
                               RedirectAttributes redirectAttributes) {
        String normalizedCode = ticketCode == null ? "" : ticketCode.trim();
        if (normalizedCode.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please enter a ticket number.");
            return "redirect:/tickets/search";
        }

        try {
            ticketService.findByTicketCode(normalizedCode);
            return "redirect:/tickets/" + normalizedCode;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", "Ticket not found. Please check your ticket number.");
            return "redirect:/tickets/search";
        }
    }

    @GetMapping("/{ticketCode}")
    public String viewTicket(@PathVariable String ticketCode, Model model) {
        Ticket ticket = ticketService.findByTicketCode(ticketCode);
        model.addAttribute("ticket", ticket);
        return "ticket/view";
    }
}
