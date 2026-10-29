package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.dto.RegisterForm;
import co.tkm.ticketmanagementsystem.enums.Role;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.service.AuthService;
import co.tkm.ticketmanagementsystem.service.PasswordResetService;
import co.tkm.ticketmanagementsystem.service.TokenService.TokenResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration and login: a client posts an email and password here and gets back an
 * OAuth2-style token pair ({@code access_token}, {@code refresh_token}) signed by this app.
 *
 * Every other endpoint in the app then expects {@code Authorization: Bearer
 * <access_token>}; nothing here establishes a session.
 *
 * Each route is exposed twice - at its original path and under {@code /api/auth/**},
 * the layout the clients, Postman collection and docs use.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration and login (bearer tokens)")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping(value = {"/login", "/api/auth/login"},
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Exchange an email and password for an access token")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        AuthService.Session session = authService.login(req.email(), req.password());
        return LoginResponse.of(session);
    }

    @PostMapping(value = {"/register", "/api/auth/register"},
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Register a new customer account")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterForm registerForm) {
        User user = authService.register(registerForm.getName(), registerForm.getEmail(), registerForm.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.of(user));
    }

    @PostMapping(value = "/api/auth/refresh",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Trade a refresh token for a fresh access token")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return LoginResponse.of(authService.refresh(req.refreshToken()));
    }

    @PostMapping(value = {"/logout", "/api/auth/logout"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Revoke a refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/api/auth/forgot-password",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Email a 6-digit password reset code",
            description = "Always 202, whether or not the email has an account, so it cannot be used to probe for accounts.")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordResetService.requestReset(req.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MessageResponse(
                "If an account exists for that email, a reset code is on its way."));
    }

    @PostMapping(value = "/api/auth/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Set a new password with the emailed code",
            description = "400 with code RESET_CODE_INVALID when the code is wrong, expired, or locked out. "
                    + "On success every session of the account is signed out.")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.resetPassword(req.email(), req.code(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record ForgotPasswordRequest(@NotBlank String email) {
    }

    public record ResetPasswordRequest(@NotBlank String email, @NotBlank String code, @NotBlank String newPassword) {
    }

    public record MessageResponse(String message) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank @JsonProperty("refresh_token") String refreshToken) {
    }

    /**
     * The token fields sit at the top level under their OAuth2 names
     * ({@code access_token}, ...), alongside the bits of the account the UI needs to
     * route the user.
     */
    public record LoginResponse(
            String message,
            String email,
            String name,
            String role,
            String redirectUrl,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("token_type") String tokenType
    ) {
        static LoginResponse of(AuthService.Session session) {
            User user = session.user();
            TokenResponse token = session.token();
            boolean isAdmin = user.getRole() == Role.ADMIN;
            return new LoginResponse(
                    "Login successful for " + user.getEmail(),
                    user.getEmail(),
                    user.getName(),
                    user.getRole().name(),
                    isAdmin ? "/admin/dashboard" : "/movies",
                    token.accessToken(), token.refreshToken(), token.expiresIn(), token.tokenType());
        }
    }

    public record RegisterResponse(String message, RegisteredUser user) {
        static RegisterResponse of(User user) {
            return new RegisterResponse("User " + user.getEmail() + " registered successfully",
                    new RegisteredUser(user.getId(), user.getName(), user.getEmail(), user.getRole().name()));
        }
    }

    public record RegisteredUser(Long id, String name, String email, String role) {
    }
}
