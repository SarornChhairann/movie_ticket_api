package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.dto.RegisterForm;
import co.tkm.ticketmanagementsystem.service.JwtService;
import co.tkm.ticketmanagementsystem.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Login and registration (session-cookie based)")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Log in with email and password; establishes an authenticated session cookie")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        Authentication authRequest = new UsernamePasswordAuthenticationToken(req.email(), req.password());
        Authentication authResult = authenticationManager.authenticate(authRequest);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authResult);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        boolean isAdmin = authResult.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        String token = jwtService.generateToken(authResult);
        return ResponseEntity.ok(new LoginResponse(
                authResult.getName(), isAdmin ? "ADMIN" : "CUSTOMER", isAdmin ? "/admin/dashboard" : "/movies", token));
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Register a new customer account")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterForm registerForm) {
        userService.registerCustomer(registerForm.getName(), registerForm.getEmail(), registerForm.getPassword());
        return ResponseEntity.ok(Map.of("success", true));
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record LoginResponse(String email, String role, String redirectUrl, String token) {
    }
}
