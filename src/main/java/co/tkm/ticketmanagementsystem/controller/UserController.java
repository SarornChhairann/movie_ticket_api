package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "View and manage the logged-in user's profile")
public class UserController {

    private final UserService userService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the current user's profile")
    public ResponseEntity<UserResponse> getProfile(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update the current user's name and phone number")
    public ResponseEntity<UserResponse> updateProfile(@RequestBody UpdateProfileRequest req,
                                                       Authentication authentication) {
        userService.updateProfile(authentication.getName(), req.name(), req.phone());
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PostMapping(value = "/change-password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Change the current user's password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest req,
                                             Authentication authentication) {
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw new IllegalArgumentException("New passwords do not match.");
        }
        if (req.newPassword() == null || req.newPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
        userService.changePassword(authentication.getName(), req.currentPassword(), req.newPassword());
        return ResponseEntity.ok().build();
    }

    public record UserResponse(Long id, String name, String email, String phone, String role,
                                int points, String status) {
        static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getRole().name(),
                    user.getPoints(),
                    user.getStatus().name()
            );
        }
    }

    public record UpdateProfileRequest(String name, String phone) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {
    }
}