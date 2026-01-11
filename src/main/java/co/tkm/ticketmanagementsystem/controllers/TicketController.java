package co.tkm.ticketmanagementsystem.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/tickets")
public class TicketController {

    @GetMapping
    String tickets(){
        return "backend/tickets/tickets";
    }

    @GetMapping("/create")
    String createTicket(){
        return "backend/tickets/create-ticket";
    }
}
