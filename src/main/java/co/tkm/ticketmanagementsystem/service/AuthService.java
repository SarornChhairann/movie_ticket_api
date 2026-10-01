package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.enums.Role;
import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.security.KeycloakClaims;
import co.tkm.ticketmanagementsystem.service.KeycloakAdminClient.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * The registration and login flows, for clients that only know about this app and
 * not about Keycloak.
 *
 * Keycloak owns credentials, roles and sessions; the {@code users} table is only a
 * mirror, kept so bookings, tickets, points and loyalty can keep pointing at a local
 * row. Every method here therefore does the Keycloak half first and mirrors second.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Error code for a SUSPENDED or BANNED account; also written by {@code KeycloakUserSyncFilter}. */
    public static final String ACCOUNT_BLOCKED = "ACCOUNT_BLOCKED";

    private final KeycloakAdminClient keycloak;
    private final UserService userService;
    private final JwtDecoder jwtDecoder;

    /**
     * Exchanges an email/password for Keycloak tokens, then refreshes the local
     * mirror from the access token that came back so the caller can be told which
     * role it logged in as.
     */
    public Session login(String email, String password) {
        TokenResponse token = keycloak.passwordGrant(normalize(email), password);
        return new Session(token, mirror(token.accessToken()));
    }

    /** Same, from a refresh token instead of a password. */
    public Session refresh(String refreshToken) {
        TokenResponse token = keycloak.refreshGrant(refreshToken);
        return new Session(token, mirror(token.accessToken()));
    }

    /**
     * Ends the Keycloak session behind the refresh token. The access token itself
     * stays valid until it expires - inherent to stateless bearer-token auth - so
     * clients should drop their copy of it too.
     */
    public void logout(String refreshToken) {
        keycloak.endSession(refreshToken);
    }

    /**
     * Creates a customer in Keycloak and mirrors it locally. Keycloak goes first
     * because it is the system of record; if the local insert then fails, the
     * half-created Keycloak account is removed rather than left stranded.
     */
    public User register(String name, String email, String password) {
        String normalized = normalize(email);
        String[] parts = splitName(name, normalized);

        // Whether the email is taken is Keycloak's call, not the mirror's: a users row
        // left behind by an account that was since deleted from the realm must not block
        // the address forever. An existing row is adopted by the mirror step below.
        String keycloakUserId;
        try {
            keycloakUserId = keycloak.createUser(normalized, normalized, parts[0], parts[1],
                    password, KeycloakClaims.CUSTOMER_REALM_ROLE);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use.");
            }
            throw e;
        }
        try {
            return userService.mirrorKeycloakUser(normalized, name, Role.CUSTOMER);
        } catch (RuntimeException e) {
            try {
                keycloak.deleteUser(keycloakUserId);
            } catch (RuntimeException cleanupFailure) {
                // Report why registration failed, not why the cleanup did.
                log.error("Registered {} in Keycloak but could not mirror it locally, and the "
                        + "rollback failed too - the realm now holds an orphan account.", normalized,
                        cleanupFailure);
            }
            throw e;
        }
    }

    /**
     * Verifies the current password by actually logging in with it - the app never
     * sees a password hash any more - and then resets the credential in Keycloak.
     */
    public void changePassword(String email, String currentPassword, String newPassword) {
        String normalized = normalize(email);
        try {
            keycloak.passwordGrant(normalized, currentPassword);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new IllegalArgumentException("Current password is incorrect.");
            }
            throw e;
        }
        keycloak.resetPassword(requireKeycloakUserId(normalized), newPassword);
    }

    /**
     * Keeps a Keycloak account in step with a status change made in the back office:
     * a SUSPENDED or BANNED user is disabled there so Keycloak stops issuing it tokens.
     * Best-effort - the local status is authoritative and is enforced per request by
     * {@code KeycloakUserSyncFilter} either way.
     */
    public void syncStatus(String email, UserStatus status) {
        try {
            keycloak.findUserId(normalize(email))
                    .ifPresent(userId -> keycloak.setEnabled(userId, status == UserStatus.ACTIVE));
        } catch (RuntimeException e) {
            log.warn("Could not mirror status {} for {} onto Keycloak: {}", status, email, e.getMessage());
        }
    }

    /** Pushes a profile rename onto the Keycloak account. Best-effort, same reasoning as above. */
    public void syncName(String email, String name) {
        try {
            String normalized = normalize(email);
            String[] parts = splitName(name, normalized);
            keycloak.findUserId(normalized)
                    .ifPresent(userId -> keycloak.updateName(userId, parts[0], parts[1]));
        } catch (RuntimeException e) {
            log.warn("Could not mirror the name of {} onto Keycloak: {}", email, e.getMessage());
        }
    }

    /**
     * Startup bootstrap for the APP_ADMIN_* account: creates it in Keycloak if it is
     * missing, and otherwise realigns its password and admin role, so the credentials
     * in the app's configuration are always the ones that actually work.
     */
    public void ensureAdminAccount(String name, String email, String password) {
        String normalized = normalize(email);
        String[] parts = splitName(name, normalized);

        Optional<String> existing = keycloak.findUserId(normalized);
        if (existing.isPresent()) {
            String userId = existing.get();
            keycloak.resetPassword(userId, password);
            keycloak.assignRealmRole(userId, KeycloakClaims.ADMIN_REALM_ROLE);
            keycloak.updateName(userId, parts[0], parts[1]);
            keycloak.setEnabled(userId, true);
        } else {
            keycloak.createUser(normalized, normalized, parts[0], parts[1], password,
                    KeycloakClaims.ADMIN_REALM_ROLE);
        }
    }

    // ---------------------------------------------------------------- plumbing

    /**
     * Decodes the token Keycloak just issued - the same validation every later
     * request goes through - and writes what it says about this user into the
     * mirror, so the local row's role can never drift from the realm's.
     */
    private User mirror(String accessToken) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(accessToken);
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Keycloak issued a token this app cannot validate - check that "
                            + "spring.security.oauth2.resourceserver.jwt.issuer-uri matches the realm "
                            + "keycloak.server-url points at: " + e.getMessage());
        }

        String email = KeycloakClaims.email(jwt);
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The token Keycloak issued carries no email claim, so it cannot be tied to an account");
        }

        User user = userService.mirrorKeycloakUser(email, KeycloakClaims.displayName(jwt), KeycloakClaims.role(jwt));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, ACCOUNT_BLOCKED,
                    "This account is " + user.getStatus().name().toLowerCase() + ".");
        }
        return user;
    }

    private String requireKeycloakUserId(String email) {
        return keycloak.findUserId(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No Keycloak account exists for " + email));
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * Keycloak's user profile requires both a first and a last name, so a one-word
     * name (or none at all) is padded rather than rejected - see
     * {@link KeycloakAdminClient#createUser}.
     */
    private static String[] splitName(String name, String fallback) {
        String trimmed = (name == null || name.isBlank()) ? fallback : name.trim();
        int split = trimmed.indexOf(' ');
        if (split < 0) {
            return new String[]{trimmed, trimmed};
        }
        return new String[]{trimmed.substring(0, split), trimmed.substring(split + 1).trim()};
    }

    /** A successful login or refresh: the Keycloak tokens plus the local mirror row. */
    public record Session(TokenResponse token, User user) {
    }
}
