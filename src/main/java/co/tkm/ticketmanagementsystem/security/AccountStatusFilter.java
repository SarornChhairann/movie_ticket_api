package co.tkm.ticketmanagementsystem.security;

import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import co.tkm.ticketmanagementsystem.service.AuthService;
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
import java.util.Optional;

/**
 * Runs right after the bearer token is validated. An access token stays valid until
 * it expires, so this is where an account deleted or locked by the back office since
 * the token was issued gets turned away: 401 if the user no longer exists, 403
 * {@code ACCOUNT_BLOCKED} if it is SUSPENDED or BANNED.
 */
@RequiredArgsConstructor
public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            chain.doFilter(request, response);
            return;
        }

        String email = TokenClaims.email(jwtAuthentication.getToken());
        Optional<User> user = email == null ? Optional.empty() : userRepository.findByEmail(email);
        if (user.isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED, null, "The account this token was issued for no longer exists.");
            return;
        }
        if (user.get().getStatus() != UserStatus.ACTIVE) {
            writeError(response, HttpStatus.FORBIDDEN, AuthService.ACCOUNT_BLOCKED,
                    "This account is " + user.get().getStatus().name().toLowerCase() + ".");
            return;
        }

        chain.doFilter(request, response);
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
