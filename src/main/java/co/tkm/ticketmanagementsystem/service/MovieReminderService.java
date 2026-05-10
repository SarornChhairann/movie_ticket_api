package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.MovieReminder;
import co.tkm.ticketmanagementsystem.repository.MovieReminderRepository;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MovieReminderService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    private final MovieReminderRepository movieReminderRepository;
    private final MovieRepository movieRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${APP_ADMIN_EMAIL:}")
    private String fallbackFrom;

    @Transactional
    public void createReminder(Long movieId, String email) {
        if (movieId == null) {
            throw new IllegalArgumentException("movieId is required.");
        }
        String normalizedEmail = normalizeEmail(email);
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new IllegalArgumentException("Movie not found: " + movieId));

        if (movieReminderRepository.existsByMovie_IdAndEmailIgnoreCase(movieId, normalizedEmail)) {
            return;
        }

        MovieReminder r = new MovieReminder();
        r.setMovie(movie);
        r.setEmail(normalizedEmail);
        movieReminderRepository.save(r);

        sendConfirmationEmail(movie, normalizedEmail);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void sendConfirmationEmail(Movie movie, String email) {
        String from = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom : fallbackFrom;
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("Email service is not configured.");
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(email);
        msg.setFrom(from);
        msg.setSubject("Reminder confirmed: " + movie.getTitle());
        msg.setText("""
                Thanks! You’re subscribed for a reminder.

                Movie: %s
                We’ll email you when tickets open.

                MTMS
                """.formatted(movie.getTitle()));
        try {
            mailSender.send(msg);
        } catch (MailException ex) {
            throw new IllegalStateException("Failed to send confirmation email.");
        }
    }
}
