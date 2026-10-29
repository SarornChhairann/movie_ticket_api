package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.enums.Role;
import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import co.tkm.ticketmanagementsystem.service.TokenService.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registration, login, refresh, logout and password changes. Credentials live in the
 * {@code users} table as BCrypt hashes; {@link TokenService} turns a verified user into
 * the access/refresh token pair every client sends back afterwards.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Error code for a SUSPENDED or BANNED account; also written by {@code AccountStatusFilter}. */
    public static final String ACCOUNT_BLOCKED = "ACCOUNT_BLOCKED";

    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional
    public Session login(String email, String password) {
        User user = userRepository.findByEmail(normalize(email))
                .filter(u -> passwordMatches(password, u))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS));
        requireActive(user);
        return new Session(tokenService.issue(user), user);
    }

    /** Trades a refresh token for a new pair; the old refresh token stops working. */
    @Transactional
    public Session refresh(String refreshToken) {
        Long userId = tokenService.consumeRefreshToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Refresh token is missing, expired, or already used"));
        requireActive(user);
        return new Session(tokenService.issue(user), user);
    }

    /**
     * Revokes the refresh token. The access token itself stays valid until it expires -
     * inherent to stateless bearer-token auth - so clients should drop their copy too.
     */
    public void logout(String refreshToken) {
        tokenService.revoke(refreshToken);
    }

    @Transactional
    public User register(String name, String email, String password) {
        String normalized = normalize(email);
        if (userRepository.findByEmail(normalized).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use.");
        }
        User user = new User();
        user.setEmail(normalized);
        user.setName((name == null || name.isBlank()) ? normalized : name.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    /** Checks the current password, stores the new one, and signs out every other session. */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(normalize(email))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        if (!passwordMatches(currentPassword, user)) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenService.revokeAll(user.getId());
    }

    /**
     * Called after the back office changes a user's status. A blocked account loses its
     * refresh tokens at once; its access token is already refused by
     * {@code AccountStatusFilter} on the next request.
     */
    public void onStatusChanged(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            tokenService.revokeAll(user.getId());
        }
    }

    /**
     * Startup bootstrap for the APP_ADMIN_* account: creates it if missing, and otherwise
     * realigns its password, role and status, so the configured credentials always work.
     */
    @Transactional
    public User ensureAdminAccount(String name, String email, String password) {
        String normalized = normalize(email);
        User admin = userRepository.findByEmail(normalized).orElseGet(User::new);
        admin.setEmail(normalized);
        admin.setName(name);
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        if (!passwordMatches(password, admin)) {
            admin.setPassword(passwordEncoder.encode(password));
        }
        return userRepository.save(admin);
    }

    // ---------------------------------------------------------------- plumbing

    private boolean passwordMatches(String raw, User user) {
        String hash = user.getPassword();
        return raw != null && hash != null && hash.startsWith("$2") && passwordEncoder.matches(raw, hash);
    }

    private static void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, ACCOUNT_BLOCKED,
                    "This account is " + user.getStatus().name().toLowerCase() + ".");
        }
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /** A successful login or refresh: the issued tokens plus the user they belong to. */
    public record Session(TokenResponse token, User user) {
    }
}
