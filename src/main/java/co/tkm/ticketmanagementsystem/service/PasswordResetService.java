package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.config.AuthProperties;
import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.PasswordResetCode;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.PasswordResetCodeRepository;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Forgot password: a 6-digit code is emailed to the account, and the code plus a new
 * password resets it. A code (not a link) because the mobile app has no deep links.
 *
 * <ul>
 *   <li>{@link #requestReset} never reveals whether an email has an account - the
 *       controller answers the same way either way.</li>
 *   <li>A code lives {@code app.auth.password-reset-ttl} (15m), a new one can be sent
 *       at most once per {@link #RESEND_INTERVAL}, and an account accepts at most
 *       {@link #MAX_FAILED_ATTEMPTS} wrong guesses per {@link #ATTEMPT_WINDOW} across
 *       all its codes, so a 6-digit space cannot be walked.</li>
 *   <li>A successful reset signs out every session of the account.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    public static final String RESET_CODE_INVALID = "RESET_CODE_INVALID";

    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    static final Duration ATTEMPT_WINDOW = Duration.ofHours(24);
    static final int MAX_FAILED_ATTEMPTS = 10;
    static final int MIN_PASSWORD_LENGTH = 6;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final AuthProperties properties;

    /** Sends a code if {@code email} belongs to an active account; silently does nothing otherwise. */
    public void requestReset(String email) {
        Optional<Issued> issued = issueCode(normalize(email));
        issued.ifPresent(i -> {
            try {
                emailService.sendPasswordResetCode(i.user().getEmail(), i.user().getName(), i.code(), resetTtl());
            } catch (RuntimeException e) {
                log.warn("Could not send the password reset code to {}: {}", i.user().getEmail(), e.getMessage());
            }
        });
    }

    /**
     * Checks the code and sets the new password. A wrong guess is counted even though
     * the request fails, hence {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(String email, String code, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        Instant now = Instant.now();
        User user = userRepository.findByEmail(normalize(email)).orElseThrow(PasswordResetService::invalidCode);
        PasswordResetCode reset = codeRepository.findByUserId(user.getId()).orElseThrow(PasswordResetService::invalidCode);
        rollWindow(reset, now);

        if (reset.getCodeHash() == null || reset.getExpiresAt() == null || reset.getExpiresAt().isBefore(now)
                || reset.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw invalidCode();
        }
        if (!MessageDigest.isEqual(hash(user.getId(), code).getBytes(StandardCharsets.UTF_8),
                reset.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
            reset.setFailedAttempts(reset.getFailedAttempts() + 1);
            codeRepository.save(reset);
            throw invalidCode();
        }

        reset.setCodeHash(null);
        reset.setExpiresAt(null);
        codeRepository.save(reset);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenService.revokeAll(user.getId());
    }

    /** Stores a fresh code, or returns empty when none may be sent right now. */
    private Optional<Issued> issueCode(String email) {
        Optional<User> found = userRepository.findByEmail(email)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        User user = found.get();
        Instant now = Instant.now();
        PasswordResetCode reset = codeRepository.findByUserId(user.getId()).orElseGet(() -> {
            PasswordResetCode fresh = new PasswordResetCode();
            fresh.setUserId(user.getId());
            fresh.setWindowStartedAt(now);
            return fresh;
        });
        rollWindow(reset, now);

        if (reset.getLastSentAt() != null && reset.getLastSentAt().plus(RESEND_INTERVAL).isAfter(now)) {
            return Optional.empty();
        }
        if (reset.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            log.warn("Password reset for {} is locked until the attempt window ends", email);
            return Optional.empty();
        }

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        reset.setCodeHash(hash(user.getId(), code));
        reset.setExpiresAt(now.plus(resetTtl()));
        reset.setLastSentAt(now);
        codeRepository.save(reset);
        return Optional.of(new Issued(user, code));
    }

    private static void rollWindow(PasswordResetCode reset, Instant now) {
        if (reset.getWindowStartedAt() == null || reset.getWindowStartedAt().plus(ATTEMPT_WINDOW).isBefore(now)) {
            reset.setWindowStartedAt(now);
            reset.setFailedAttempts(0);
        }
    }

    private Duration resetTtl() {
        return properties.passwordResetTtl() == null ? Duration.ofMinutes(15) : properties.passwordResetTtl();
    }

    private static ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, RESET_CODE_INVALID,
                "That code is wrong or has expired. Request a new one.");
    }

    /** Salted with the user id so the same code for two users never hashes alike. */
    private static String hash(Long userId, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((userId + ":" + (code == null ? "" : code.trim())).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    record Issued(User user, String code) {
    }
}
