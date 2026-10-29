package co.tkm.ticketmanagementsystem.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/**
 * The shape of the access tokens {@code TokenService} issues, in one place.
 *
 * <pre>{ "sub": "dara@example.com", "name": "Dara Sok", "roles": ["CUSTOMER"], ... }</pre>
 *
 * The subject is the user's email, because every controller and service resolves the
 * current user with {@code authentication.getName()} against {@code users.email}.
 */
public final class TokenClaims {

    public static final String NAME = "name";
    public static final String ROLES = "roles";

    private TokenClaims() {
    }

    public static String email(Jwt jwt) {
        String subject = jwt.getSubject();
        return (subject == null || subject.isBlank()) ? null : subject.trim();
    }

    /** Role names as stored on the user ({@code ADMIN}, {@code CUSTOMER}), without the ROLE_ prefix. */
    public static Collection<String> roles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES);
        return roles == null ? List.of() : roles;
    }
}
