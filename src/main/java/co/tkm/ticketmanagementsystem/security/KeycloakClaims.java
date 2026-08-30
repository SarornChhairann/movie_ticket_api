package co.tkm.ticketmanagementsystem.security;

import co.tkm.ticketmanagementsystem.enums.Role;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Everything this app knows about the shape of a Keycloak access token, in one place.
 *
 * Keycloak realm roles live under the {@code realm_access.roles} claim:
 * <pre>"realm_access": { "roles": ["customer", "admin"] }</pre>
 * and the app's own identity for a user is their email, which is what every
 * service here looks users up by.
 */
public final class KeycloakClaims {

    /** Realm role every self-registered account gets; maps to {@link Role#CUSTOMER}. */
    public static final String CUSTOMER_REALM_ROLE = "customer";

    /** Realm role behind {@code ROLE_ADMIN} and every {@code /admin/**} rule. */
    public static final String ADMIN_REALM_ROLE = "admin";

    private KeycloakClaims() {
    }

    /**
     * The email the token was issued for. Accounts are always created with
     * email-as-username, so {@code preferred_username} is a safe fallback for a
     * token minted before that convention (or by hand in the Keycloak console).
     */
    public static String email(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("preferred_username");
        }
        return (email == null || email.isBlank()) ? null : email.trim();
    }

    /** Display name for the mirrored {@code users} row, assembled from whatever Keycloak sent. */
    public static String displayName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        String joined = ((given == null ? "" : given) + " " + (family == null ? "" : family)).trim();
        if (!joined.isBlank()) {
            return joined;
        }
        return email(jwt);
    }

    @SuppressWarnings("unchecked")
    public static Collection<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return (Collection<String>) roles;
    }

    /**
     * The app only distinguishes two kinds of account, so any token carrying the
     * {@code admin} realm role mirrors as an ADMIN row and everything else as a CUSTOMER.
     */
    public static Role role(Jwt jwt) {
        return realmRoles(jwt).contains(ADMIN_REALM_ROLE) ? Role.ADMIN : Role.CUSTOMER;
    }
}
