package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.config.AuthProperties;
import co.tkm.ticketmanagementsystem.model.RefreshToken;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.RefreshTokenRepository;
import co.tkm.ticketmanagementsystem.security.TokenClaims;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Issues the token pair behind {@code /login} and {@code /api/auth/refresh}:
 *
 * <ul>
 *   <li>a short-lived, signed JWT access token, verified on every request by the
 *       resource server without touching the database;</li>
 *   <li>an opaque refresh token, kept (hashed) in {@code refresh_tokens} so it can be
 *       rotated on use and revoked on logout.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;
    private final RefreshTokenRepository refreshTokenRepository;

    /** A fresh access token plus a new refresh token for {@code user}. */
    @Transactional
    public TokenResponse issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getEmail())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(TokenClaims.NAME, user.getName())
                .claim(TokenClaims.ROLES, List.of(user.getRole().name()))
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        String refreshToken = newOpaqueToken();
        RefreshToken stored = new RefreshToken();
        stored.setTokenHash(hash(refreshToken));
        stored.setUserId(user.getId());
        stored.setCreatedAt(now);
        stored.setExpiresAt(now.plus(properties.refreshTokenTtl()));
        refreshTokenRepository.save(stored);

        return new TokenResponse(accessToken, refreshToken,
                properties.accessTokenTtl().toSeconds(), "Bearer");
    }

    /**
     * Spends a refresh token: it is deleted, so it cannot be used twice, and the id of
     * the user it belonged to is returned so the caller can issue a new pair. Expired
     * tokens are rejected here and left for {@link #purgeExpired()}.
     */
    @Transactional
    public Long consumeRefreshToken(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(TokenService::invalidRefreshToken);
        refreshTokenRepository.delete(stored);
        return stored.getUserId();
    }

    /** Logout. An unknown or already-used token is not an error: the session is over either way. */
    @Transactional
    public void revoke(String refreshToken) {
        refreshTokenRepository.deleteByTokenHash(hash(refreshToken));
    }

    /** Ends every session of a user (password change, suspension). */
    @Transactional
    public void revokeAll(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = refreshTokenRepository.deleteExpired(Instant.now());
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }

    private static ResponseStatusException invalidRefreshToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Refresh token is missing, expired, or already used");
    }

    private static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Same field names as an OAuth2 token endpoint, which is what the clients already read. */
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("token_type") String tokenType
    ) {
    }
}
