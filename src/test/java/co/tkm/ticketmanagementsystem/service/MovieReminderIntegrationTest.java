package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.repository.MovieReminderRepository;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Sprint 7 against H2: duplicates are {@code REMINDER_EXISTS} (G21), and the row
 * survives a failing mail server because the email is best-effort after commit (G22).
 */
@SpringBootTest(properties = "spring.mail.username=noreply@test.local")
class MovieReminderIntegrationTest {

    @Autowired private MovieReminderService movieReminderService;
    @Autowired private MovieReminderRepository movieReminderRepository;
    @Autowired private MovieRepository movieRepository;

    @MockitoBean private JavaMailSender mailSender;

    private Movie comingSoon;

    @BeforeEach
    void seed() {
        comingSoon = movie(MovieStatus.COMING_SOON);
    }

    private Movie movie(MovieStatus status) {
        Movie movie = new Movie();
        movie.setTitle("Reminder IT " + UUID.randomUUID().toString().substring(0, 8));
        movie.setGenre("Drama");
        movie.setDurationMinutes(100);
        movie.setStatus(status);
        movie.setReleaseDate(LocalDate.now().plusMonths(1));
        return movieRepository.save(movie);
    }

    private long rowsFor(Movie movie) {
        return movieReminderRepository.findAll().stream()
                .filter(r -> r.getMovie().getId().equals(movie.getId()))
                .count();
    }

    @Test
    void sameEmailAndMovieTwiceKeepsOneRowAndIsReminderExists() {
        movieReminderService.createReminder(comingSoon.getId(), "dara@example.com");

        ApiException ex = assertThrows(ApiException.class,
                () -> movieReminderService.createReminder(comingSoon.getId(), "dara@example.com"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(MovieReminderService.REMINDER_EXISTS);
        assertThat(rowsFor(comingSoon)).isEqualTo(1);
    }

    @Test
    void duplicateCheckIgnoresEmailCase() {
        movieReminderService.createReminder(comingSoon.getId(), "Dara@Example.com");

        ApiException ex = assertThrows(ApiException.class,
                () -> movieReminderService.createReminder(comingSoon.getId(), "  dara@EXAMPLE.com "));

        assertThat(ex.getCode()).isEqualTo(MovieReminderService.REMINDER_EXISTS);
        assertThat(rowsFor(comingSoon)).isEqualTo(1);
    }

    @Test
    void mailFailureStillSavesTheRow() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> movieReminderService.createReminder(comingSoon.getId(), "sok@example.com"));

        verify(mailSender).send(any(SimpleMailMessage.class));
        assertThat(rowsFor(comingSoon)).isEqualTo(1);
    }

    @Test
    void nowShowingMovieIsRejected() {
        Movie nowShowing = movie(MovieStatus.NOW_SHOWING);

        assertThrows(IllegalArgumentException.class,
                () -> movieReminderService.createReminder(nowShowing.getId(), "dara@example.com"));
        assertThat(rowsFor(nowShowing)).isZero();
    }
}
