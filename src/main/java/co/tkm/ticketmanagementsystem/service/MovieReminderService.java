package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.MovieReminder;
import co.tkm.ticketmanagementsystem.repository.MovieReminderRepository;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


@Slf4j
@Service
@RequiredArgsConstructor
public class MovieReminderService {

    public static final String REMINDER_EXISTS = "REMINDER_EXISTS";

    private final MovieReminderRepository movieReminderRepository;
    private final MovieRepository movieRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${APP_ADMIN_EMAIL:}")
    private String fallbackFrom;

    /**
     * Subscribes [email] to a coming-soon movie. The row is the commitment; the
     * confirmation email is sent after commit and only best-effort, so a missing or
     * broken mail server never loses the subscription (G22).
     */
    @Transactional
    public void createReminder(Long movieId, String email) {
        if (movieId == null) {
            throw new IllegalArgumentException("movieId is required.");
        }
        String normalizedEmail = EmailAddress.normalize(email);
        if (!EmailAddress.isValid(normalizedEmail)) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new IllegalArgumentException("Movie not found: " + movieId));
        if (movie.getStatus() != MovieStatus.COMING_SOON) {
            throw new IllegalArgumentException("Reminders are only available for coming-soon movies.");
        }

        if (movieReminderRepository.existsByMovie_IdAndEmailIgnoreCase(movieId, normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, REMINDER_EXISTS,
                    "You are already subscribed to reminders for this movie.");
        }

        MovieReminder r = new MovieReminder();
        r.setMovie(movie);
        r.setEmail(normalizedEmail);
        movieReminderRepository.save(r);

        String title = movie.getTitle();
        afterCommit(() -> sendConfirmationEmail(title, normalizedEmail));
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** Best-effort: logs and returns when mail is not configured or fails. */
    void sendConfirmationEmail(String movieTitle, String email) {
        String from = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom : fallbackFrom;
        if (from == null || from.isBlank()) {
            log.warn("Reminder for {} saved; no confirmation email sent (mail sender not configured).", movieTitle);
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(email);
        msg.setFrom(from);
        msg.setSubject("Reminder confirmed: " + movieTitle);
        msg.setText("""
                Thanks! You’re subscribed for a reminder.

                Movie: %s
                We’ll email you when tickets open.

                MTMS
                """.formatted(movieTitle));
        try {
            mailSender.send(msg);
        } catch (MailException ex) {
            log.warn("Reminder for {} saved; confirmation email failed: {}", movieTitle, ex.getMessage());
        }
    }
}
