package co.tkm.ticketmanagementsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for the tokens this app issues itself ({@code app.auth.*}).
 *
 * <ul>
 *   <li>{@code jwtSecret} — HMAC key that signs and verifies every access token
 *       (HS256, so at least 32 bytes). Anyone holding it can mint tokens, so it only
 *       ever comes from the environment in production.</li>
 *   <li>{@code issuer} — written into and required in the {@code iss} claim.</li>
 *   <li>{@code accessTokenTtl} — how long a bearer token is accepted. Short, because
 *       a stateless token cannot be revoked before it expires.</li>
 *   <li>{@code refreshTokenTtl} — how long a refresh token can be traded for a new
 *       pair. These are stored server side, so logout revokes them.</li>
 *   <li>{@code passwordResetTtl} — how long an emailed forgot-password code works.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration passwordResetTtl
) {
}
