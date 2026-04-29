package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.dto.RegisterForm;
import co.tkm.ticketmanagementsystem.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registerForm", new RegisterForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm registerForm,
                           BindingResult bindingResult,
                           Model model) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.registerCustomer(registerForm.getName(), registerForm.getEmail(), registerForm.getPassword());
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return "auth/register";
        }
        return "redirect:/login?registered";
    }
}
