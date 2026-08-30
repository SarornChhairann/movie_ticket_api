package co.tkm.ticketmanagementsystem.controller.admin;

import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.service.AuthService;
import co.tkm.ticketmanagementsystem.service.BookingService;
import co.tkm.ticketmanagementsystem.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin: Users", description = "Back-office user management")
public class AdminUserController {

    private final UserService userService;
    private final BookingService bookingService;
    private final AuthService authService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List users, optionally filtered by a search query")
    public ResponseEntity<List<UserSummary>> users(@RequestParam(required = false) String q) {
        List<User> users = (q != null && !q.isBlank()) ? userService.search(q) : userService.findAll();
        return ResponseEntity.ok(users.stream().map(this::toUserSummary).toList());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a user's profile and booking history")
    @Transactional(readOnly = true)
    public ResponseEntity<UserDetailResponse> userDetail(@PathVariable Long id) {
        User user = userService.findById(id);
        List<BookingSummary> bookings = bookingService.getBookingsByUser(user).stream()
                .map(BookingSummary::from)
                .toList();
        return ResponseEntity.ok(new UserDetailResponse(toUserSummary(user), bookings, UserStatus.values()));
    }

    @PostMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Set a user's account status")
    public ResponseEntity<UserSummary> setUserStatus(@PathVariable Long id, @RequestBody StatusRequest req) {
        userService.setStatus(id, req.status());
        User user = userService.findById(id);
        // Disable the account in Keycloak too, so a suspended user stops being issued
        // tokens rather than only being turned away at this app's door.
        authService.syncStatus(user.getEmail(), req.status());
        return ResponseEntity.ok(toUserSummary(user));
    }

    @PostMapping(value = "/{id}/points", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Adjust a user's loyalty points by a delta (positive or negative)")
    public ResponseEntity<UserSummary> adjustUserPoints(@PathVariable Long id, @RequestBody PointsRequest req) {
        userService.adjustPoints(id, req.delta());
        return ResponseEntity.ok(toUserSummary(userService.findById(id)));
    }

    private UserSummary toUserSummary(User u) {
        return new UserSummary(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole().name(),
                u.getPoints(), u.getStatus().name());
    }

    public record UserSummary(Long id, String name, String email, String phone, String role, int points, String status) {
    }

    public record UserDetailResponse(UserSummary user, List<BookingSummary> bookings, UserStatus[] statuses) {
    }

    public record StatusRequest(UserStatus status) {
    }

    public record PointsRequest(int delta) {
    }
}
