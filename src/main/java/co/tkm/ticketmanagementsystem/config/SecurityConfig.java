package co.tkm.ticketmanagementsystem.config;

import co.tkm.ticketmanagementsystem.security.KeycloakClaims;
import co.tkm.ticketmanagementsystem.security.KeycloakUserSyncFilter;
import co.tkm.ticketmanagementsystem.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The app is an OAuth2 Resource Server: Keycloak owns login, credentials and roles,
 * and this app only ever inspects the signed JWT that Keycloak issued. There is no
 * session and no local password check - {@code AuthController} hands a client its
 * token, and every request after that carries {@code Authorization: Bearer <token>}.
 *
 * Keycloak's realm roles are mapped onto Spring Security authorities, so the existing
 * {@code hasRole("ADMIN")} rules and {@code ROLE_ADMIN} checks keep working unchanged.
 */
@Configuration
@EnableMethodSecurity // enables @PreAuthorize on controller/service methods
public class SecurityConfig {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   UserService userService,
                                                   ObjectMapper objectMapper) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // Stateless bearer-token API: nothing rides on a cookie, so there is no
                // CSRF vector for a token-authenticated request to protect against.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // There is no session to invalidate, and POST /logout is a real endpoint
                // here (it revokes the refresh token in Keycloak) - left enabled, the
                // default LogoutFilter would answer it with a redirect of its own and the
                // controller would never run.
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // How a client gets a token in the first place.
                        .requestMatchers(HttpMethod.POST, "/login", "/register", "/logout").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/", "/movies", "/movies/**",
                                "/bookings/seats/**", "/bookings/checkout/**", "/bookings/quote/**", "/bookings/confirmation/**",
                                "/bookings/payway/**", "/bookings/lookup",
                                "/tickets", "/tickets/**",
                                "/css/**", "/js/**", "/static/**", "/uploads/**", "/api/**", "/ws/**", "/ws", "/error",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/bookings/my-bookings", "/bookings/cancel/**",
                                "/profile", "/profile/**").authenticated()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(jsonAccessDeniedHandler(objectMapper))
                )
                // Runs once the token has been validated, so the local users row that
                // bookings and tickets point at exists before any controller needs it.
                .addFilterAfter(new KeycloakUserSyncFilter(userService, objectMapper),
                        BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Keycloak puts realm roles under {@code realm_access.roles} in lower case
     * ({@code "roles": ["customer", "admin"]}), while this app has always spelled its
     * authorities {@code ROLE_ADMIN} / {@code ROLE_CUSTOMER}, so each role is upper-cased
     * on the way in.
     *
     * The principal name is the user's email rather than Keycloak's {@code sub}, because
     * every controller and service here resolves the current user with
     * {@code authentication.getName()} against the {@code users.email} column.
     */
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> new JwtAuthenticationToken(jwt, authorities(jwt), KeycloakClaims.email(jwt));
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        return KeycloakClaims.realmRoles(jwt).stream()
                .map(role -> "ROLE_" + role.toUpperCase())
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .toList();
    }

    /**
     * Security rejects a request inside a filter, long before any controller or
     * {@code @RestControllerAdvice} runs, so those 401/403 responses have to write their
     * own body or the client just gets a blank one. Both handlers below call the standard
     * bearer-token handler first, so the RFC 6750 {@code WWW-Authenticate} header is still
     * set, and only then write the {@code error}/{@code type} body every other failure in
     * this app also returns.
     */
    private AuthenticationEntryPoint jsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
        return (request, response, authException) -> {
            delegate.commence(request, response, authException);

            String message = request.getHeader(HttpHeaders.AUTHORIZATION) == null
                    ? "No bearer token was sent, and this endpoint requires authentication."
                    : "The bearer token is missing, malformed, expired, or not signed by Keycloak.";

            writeError(objectMapper, response, statusOf(response, HttpStatus.UNAUTHORIZED), message);
        };
    }

    private AccessDeniedHandler jsonAccessDeniedHandler(ObjectMapper objectMapper) {
        BearerTokenAccessDeniedHandler delegate = new BearerTokenAccessDeniedHandler();
        return (request, response, deniedException) -> {
            delegate.handle(request, response, deniedException);

            writeError(objectMapper, response, statusOf(response, HttpStatus.FORBIDDEN),
                    "Your token is valid, but this endpoint requires a role you do not have.");
        };
    }

    /** Keep whatever status the delegate chose, falling back to the expected one. */
    private static HttpStatus statusOf(HttpServletResponse response, HttpStatus fallback) {
        HttpStatus status = HttpStatus.resolve(response.getStatus());
        return (status != null && status.isError()) ? status : fallback;
    }

    private static void writeError(ObjectMapper objectMapper, HttpServletResponse response,
                                   HttpStatus status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("type", status.name());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
