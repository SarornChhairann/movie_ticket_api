package co.tkm.ticketmanagementsystem.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class HomeController {
    @GetMapping
    String home(){
        return "backend/index";
    }

    @GetMapping("/tickets/")
    String allTickets(){
        return "backend/tickets/";
    }
}
