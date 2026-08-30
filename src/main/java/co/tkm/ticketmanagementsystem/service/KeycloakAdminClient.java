package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.config.KeycloakProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The only place in the app that speaks Keycloak's HTTP API. Two endpoints are used:
 *
 * <ul>
 *   <li>the token endpoint, for the password/refresh grants behind {@code /login}
 *       and {@code /api/auth/refresh}, and for this app's own service-account token;</li>
 *   <li>the Admin REST API, for creating and maintaining users - Keycloak is the
 *       system of record for credentials, the {@code users} table only mirrors it.</li>
 * </ul>
 *
 * Callers get back plain values or {@link ResponseStatusException}s; nothing about
 * {@code RestClient} leaks out of this class.
 */
@Service
public class KeycloakAdminClient {

    /** Re-fetch the service-account token this long before it actually expires. */
    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofSeconds(20);

    private final RestClient restClient;
    private final KeycloakProperties properties;

    private String cachedAdminToken;
    private Instant cachedAdminTokenExpiry = Instant.EPOCH;

    public KeycloakAdminClient(KeycloakProperties properties) {
        this.properties = properties;

        // Without explicit timeouts a wedged Keycloak would hang every login request.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        this.restClient = RestClient.builder()
                .baseUrl(properties.serverUrl())
                .requestFactory(requestFactory)
                .build();
    }

    // ----------------------------------------------------------------- tokens

    /** Exchanges a username/password for a token set (OAuth2 password grant). */
    public TokenResponse passwordGrant(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", properties.loginClientId());
        form.add("username", username);
        form.add("password", password);
        return requestToken(form, "Invalid email or password");
    }

    /** Trades a still-valid refresh token for a fresh access token. */
    public TokenResponse refreshGrant(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", properties.loginClientId());
        form.add("refresh_token", refreshToken);
        return requestToken(form, "Refresh token is missing, expired, or already used");
    }

    /**
     * Ends the Keycloak session behind a refresh token. Access tokens stay valid
     * until they expire - that is inherent to stateless JWT auth - but the session
     * can no longer be refreshed.
     */
    public void endSession(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.loginClientId());
        form.add("refresh_token", refreshToken);

        exchange(() -> restClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/logout", properties.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity());
    }

    // ------------------------------------------------------------- user admin

    /**
     * Creates a user and gives it {@code realmRole}. Returns Keycloak's user id,
     * which is also the {@code sub} claim of every token that user will be issued.
     */
    public String createUser(String username, String email, String firstName, String lastName,
                             String password, String realmRole) {
        Map<String, Object> payload = Map.of(
                "username", username,
                "email", email,
                // Keycloak's declarative user profile marks these required: an account
                // created without them is "not fully set up", so Keycloak fires the
                // VERIFY_PROFILE required action and the password grant - which cannot
                // run an interactive action - fails with invalid_grant.
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", password,
                        "temporary", false)));

        String adminToken = adminToken();
        ResponseEntity<Void> created = exchange(() -> restClient.post()
                .uri("/admin/realms/{realm}/users", properties.realm())
                .headers(h -> h.setBearerAuth(adminToken))
                .body(payload)
                .retrieve()
                .toBodilessEntity());

        String userId = userIdFromLocation(created);
        assignRealmRole(userId, realmRole);
        return userId;
    }

    /** Looks a user up by username (this app always registers email-as-username). */
    public Optional<String> findUserId(String username) {
        String adminToken = adminToken();
        List<Map<String, Object>> matches = exchange(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/admin/realms/{realm}/users")
                        .queryParam("username", username)
                        .queryParam("exact", true)
                        .build(properties.realm()))
                .headers(h -> h.setBearerAuth(adminToken))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }));

        if (matches == null || matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(matches.get(0).get("id")).map(Object::toString);
    }

    public void assignRealmRole(String userId, String realmRole) {
        String adminToken = adminToken();
        Map<String, Object> role = exchange(() -> restClient.get()
                .uri("/admin/realms/{realm}/roles/{role}", properties.realm(), realmRole)
                .headers(h -> h.setBearerAuth(adminToken))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));

        exchange(() -> restClient.post()
                .uri("/admin/realms/{realm}/users/{userId}/role-mappings/realm", properties.realm(), userId)
                .headers(h -> h.setBearerAuth(adminToken))
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity());
    }

    public void resetPassword(String userId, String newPassword) {
        String adminToken = adminToken();
        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", newPassword,
                "temporary", false);

        exchange(() -> restClient.put()
                .uri("/admin/realms/{realm}/users/{userId}/reset-password", properties.realm(), userId)
                .headers(h -> h.setBearerAuth(adminToken))
                .body(credential)
                .retrieve()
                .toBodilessEntity());
    }

    /** Mirrors a SUSPENDED/BANNED status onto Keycloak, so the account stops getting tokens. */
    public void setEnabled(String userId, boolean enabled) {
        updateUser(userId, Map.of("enabled", enabled));
    }

    public void updateName(String userId, String firstName, String lastName) {
        updateUser(userId, Map.of("firstName", firstName, "lastName", lastName));
    }

    public void deleteUser(String userId) {
        String adminToken = adminToken();
        exchange(() -> restClient.delete()
                .uri("/admin/realms/{realm}/users/{userId}", properties.realm(), userId)
                .headers(h -> h.setBearerAuth(adminToken))
                .retrieve()
                .toBodilessEntity());
    }

    private void updateUser(String userId, Map<String, Object> changes) {
        String adminToken = adminToken();
        exchange(() -> restClient.put()
                .uri("/admin/realms/{realm}/users/{userId}", properties.realm(), userId)
                .headers(h -> h.setBearerAuth(adminToken))
                .body(changes)
                .retrieve()
                .toBodilessEntity());
    }

    // ---------------------------------------------------------------- plumbing

    /**
     * The service-account token used for every Admin REST call. Keycloak issues
     * these with a short lifetime, so it is cached until shortly before expiry
     * rather than fetched once or fetched per request.
     */
    private synchronized String adminToken() {
        if (cachedAdminToken != null && Instant.now().isBefore(cachedAdminTokenExpiry)) {
            return cachedAdminToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.adminClientId());
        form.add("client_secret", properties.adminClientSecret());

        TokenResponse token = requestToken(form, "This app could not authenticate against Keycloak");
        cachedAdminToken = token.accessToken();
        cachedAdminTokenExpiry = Instant.now().plusSeconds(token.expiresIn()).minus(TOKEN_REFRESH_MARGIN);
        return cachedAdminToken;
    }

    /**
     * The token endpoint answers a rejected credential with 400 invalid_grant as often
     * as with 401, so both mean the same thing here - unlike on the Admin API below,
     * where a 400 really is a malformed request.
     */
    private TokenResponse requestToken(MultiValueMap<String, String> form, String unauthorizedMessage) {
        try {
            return restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.BadRequest e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, unauthorizedMessage);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not reach Keycloak at " + properties.serverUrl() + ": " + e.getMessage());
        }
    }

    /**
     * Keycloak answers a successful user creation with 201 and no body, putting the
     * new user's id at the end of the Location header - reading it here saves a
     * follow-up lookup by username.
     */
    private static String userIdFromLocation(ResponseEntity<Void> created) {
        URI location = created.getHeaders().getLocation();
        if (location == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Keycloak created the user but returned no Location header");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * Turns the Admin API's HTTP failures into statuses this API can return: a duplicate
     * user is a 409, a rejected payload a 400, and a Keycloak that is down or
     * misconfigured a 502 - never an opaque 500.
     */
    private <T> T exchange(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Keycloak rejected this app's service-account token");
        } catch (HttpClientErrorException.BadRequest e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Keycloak rejected this request: " + e.getResponseBodyAsString());
        } catch (HttpClientErrorException.Conflict e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That username or email already exists in Keycloak");
        } catch (HttpClientErrorException.Forbidden e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Keycloak refused the request: the '" + properties.adminClientId()
                            + "' service account is missing the realm-management roles it needs");
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Keycloak has no such user, role, or realm");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not reach Keycloak at " + properties.serverUrl() + ": " + e.getMessage());
        }
    }

    /** Keycloak's token endpoint wire format. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("token_type") String tokenType
    ) {
    }
}
