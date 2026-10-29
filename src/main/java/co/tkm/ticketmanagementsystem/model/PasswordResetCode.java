package co.tkm.ticketmanagementsystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The forgot-password state of one user: at most one live code (stored as a hash) plus
 * the counters that throttle it. The row outlives a used code so the daily guess cap
 * cannot be reset by simply asking for a new one.
 *
 * {@code userId} is a plain column for the same reason as {@link RefreshToken}.
 */
@Entity
@Table(name = "password_reset_codes")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** SHA-256 of the live code; null once it has been used. */
    @Column(name = "code_hash", length = 64)
    private String codeHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    /** Wrong guesses since {@link #windowStartedAt}, across every code sent in that window. */
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;
}
