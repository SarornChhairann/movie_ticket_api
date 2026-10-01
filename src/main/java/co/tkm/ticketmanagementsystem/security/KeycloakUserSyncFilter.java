package co.tkm.ticketmanagementsystem.security;

import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.service.AuthService;
import co.tkm.ticketmanagementsystem.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges a validated Keycloak token to a row in the {@code users} table.
 *
 * A token is proof of identity, but the rest of this app works with {@code User}
 * entities - bookings, tickets and points all point at one - and an account can be
 * created straight in the Keycloak console, so the mirror has to be able to appear
 * mid-flight. This filter runs right after the bearer token is validated and, for
 * every authenticated request:
 *
 * <ul>
 *   <li>creates the local row if this is the first time the app has seen the account,
 *       and realigns its role if the realm's roles changed since last time;</li>
 *   <li>rejects the request when that row is SUSPENDED or BANNED - Keycloak will keep
 *       issuing tokens to an account the back office has locked, so the check that
 *       used to live in {@code CustomUserDetailsService} has to happen here instead.</li>
 * </ul>
 */
@RequiredArgsConstructor
public class KeycloakUserSyncFilter extends OncePerRequestFilter {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            chain.doFilter(request, response);
            return;
        }

        String email = KeycloakClaims.email(jwtAuthentication.getToken());
        if (email == null) {
            writeError(response, HttpStatus.UNAUTHORIZED,
                    "This token carries no email claim, so it cannot be tied to an account");
            return;
        }

        User user = userService.mirrorKeycloakUser(email,
                KeycloakClaims.displayName(jwtAuthentication.getToken()),
                KeycloakClaims.role(jwtAuthentication.getToken()));

        if (user.getStatus() != UserStatus.ACTIVE) {
            writeError(response, HttpStatus.FORBIDDEN, AuthService.ACCOUNT_BLOCKED,
                    "This account is " + user.getStatus().name().toLowerCase() + ".");
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        writeError(response, status, null, message);
    }

    /** Same {@code error}/{@code type}/{@code code?} body shape that {@code ApiExceptionHandler} returns. */
    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("type", status.name());
        if (code != null) {
            body.put("code", code);
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
