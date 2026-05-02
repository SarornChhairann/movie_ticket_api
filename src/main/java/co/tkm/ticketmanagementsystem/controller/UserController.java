package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public String profilePage(Authentication authentication, Model model) {
        User user = userService.findByEmail(authentication.getName());
        model.addAttribute("user", user);
        return "user/profile";
    }

    @PostMapping
    public String updateProfile(@RequestParam("name") String name,
                                @RequestParam(value = "phone", required = false) String phone,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            userService.updateProfile(authentication.getName(), name, phone);
            redirectAttributes.addFlashAttribute("success", "Profile updated successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam("currentPassword") String currentPassword,
                                 @RequestParam("newPassword") String newPassword,
                                 @RequestParam("confirmPassword") String confirmPassword,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("pwError", "New passwords do not match.");
            return "redirect:/profile";
        }
        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("pwError", "Password must be at least 6 characters.");
            return "redirect:/profile";
        }
        try {
            userService.changePassword(authentication.getName(), currentPassword, newPassword);
            redirectAttributes.addFlashAttribute("pwSuccess", "Password changed successfully.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("pwError", ex.getMessage());
        }
        return "redirect:/profile";
    }
}
