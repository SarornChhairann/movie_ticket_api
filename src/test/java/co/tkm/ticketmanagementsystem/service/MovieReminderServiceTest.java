package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.MovieReminder;
import co.tkm.ticketmanagementsystem.repository.MovieReminderRepository;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieReminderServiceTest {

    @Mock private MovieReminderRepository movieReminderRepository;
    @Mock private MovieRepository movieRepository;
    @Mock private JavaMailSender mailSender;

    @InjectMocks private MovieReminderService movieReminderService;

    @Test
    void withoutAMailSenderTheReminderIsStillSaved() {
        Movie movie = new Movie();
        movie.setId(7L);
        movie.setTitle("Dune 3");
        movie.setStatus(MovieStatus.COMING_SOON);
        when(movieRepository.findById(7L)).thenReturn(Optional.of(movie));

        // mailFrom and fallbackFrom are unset: dev without SMTP.
        assertDoesNotThrow(() -> movieReminderService.createReminder(7L, "dara@example.com"));

        verify(movieReminderRepository).save(any(MovieReminder.class));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void invalidEmailIsRejectedBeforeAnyLookup() {
        assertThrows(IllegalArgumentException.class, () -> movieReminderService.createReminder(7L, "not-an-email"));
        verify(movieRepository, never()).findById(any());
    }
}
