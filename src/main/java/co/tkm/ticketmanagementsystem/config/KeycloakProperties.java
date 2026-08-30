package co.tkm.ticketmanagementsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything the app needs to talk to Keycloak itself, separately from
 * {@code spring.security.oauth2.resourceserver.jwt.*} (which only ever verifies
 * tokens, never issues them or creates users).
 *
 * <ul>
 *   <li>{@code loginClientId} — public client used to exchange an email/password
 *       for a token (the password grant behind {@code POST /login}).</li>
 *   <li>{@code adminClientId}/{@code adminClientSecret} — confidential client whose
 *       service account holds {@code realm-management: manage-users}, used to create
 *       and maintain users through Keycloak's Admin REST API.</li>
 *   <li>{@code bootstrapAdmin} — whether {@code AdminSeeder} should also push the
 *       APP_ADMIN_* account into Keycloak at startup. Turned off for tests, which
 *       have no Keycloak to talk to.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakProperties(
        String serverUrl,
        String realm,
        String loginClientId,
        String adminClientId,
        String adminClientSecret,
        boolean bootstrapAdmin
) {
}
