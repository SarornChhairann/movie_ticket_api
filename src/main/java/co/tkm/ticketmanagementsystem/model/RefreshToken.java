package co.tkm.ticketmanagementsystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One live refresh token. Only a SHA-256 hash of the token is stored, so a leaked
 * table cannot be replayed. Each token is single-use: a refresh deletes it and issues
 * a new one, and logout, a password change or a suspension delete them outright.
 *
 * {@code userId} is a plain column rather than a relation so the demo seeder's bulk
 * user wipe is not blocked by a foreign key; a token whose user is gone is rejected.
 */
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_tokens_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
