package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Cinema;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.service.BookingService;
import co.tkm.ticketmanagementsystem.service.HallService;
import co.tkm.ticketmanagementsystem.service.MovieService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieControllerTest {

    @Mock private MovieService movieService;
    @Mock private ShowtimeRepository showtimeRepository;
    @Mock private HallService hallService;
    @Mock private BookingService bookingService;

    @InjectMocks
    private MovieController controller;

    private static Showtime showtime(long id, LocalDate date, boolean blocked, Hall hall, Movie movie) {
        Showtime s = new Showtime();
        s.setId(id);
        s.setMovie(movie);
        s.setHall(hall);
        s.setShowDate(date);
        s.setShowTime(LocalTime.of(19, 30));
        s.setNormalPrice(new BigDecimal("6.00"));
        s.setCouplePrice(new BigDecimal("12.00"));
        s.setBlocked(blocked);
        return s;
    }

    @Test
    void list_passesTheTabStatusSoArchivedMoviesAreNotRequested() {
        controller.list("NOW_SHOWING", null, null, null, null, null, null, null);

        verify(movieService).filterMovies(eq(MovieStatus.NOW_SHOWING), any(), any(), any(), any(), any());
    }

    @Test
    void detail_hidesBlockedAndPastShowtimesAndAddsCinemaAndHallFlags() {
        Movie movie = new Movie();
        movie.setId(1L);
        movie.setTitle("Dune");
        movie.setStatus(MovieStatus.NOW_SHOWING);

        Cinema cinema = new Cinema();
        cinema.setName("Legend Toul Kork");
        Hall hall = new Hall();
        hall.setId(3L);
        hall.setName("Hall 3");
        hall.setCinema(cinema);
        hall.setHasDolbyAtmos(true);

        LocalDate future = LocalDate.now().plusDays(3);
        Showtime bookable = showtime(10L, future, false, hall, movie);
        Showtime blocked = showtime(11L, future, true, hall, movie);
        Showtime past = showtime(12L, LocalDate.now().minusDays(2), false, hall, movie);

        when(movieService.findById(1L)).thenReturn(movie);
        when(showtimeRepository.findByMovieIdWithHall(1L)).thenReturn(List.of(past, bookable, blocked));

        MovieController.MovieDetailResponse body = controller.detail(1L, null, null, null, null).getBody();

        assertThat(body).isNotNull();
        assertThat(body.availableDates()).containsExactly(future);
        List<MovieController.ShowtimeSummary> slots = body.showtimesByDate().get(future);
        assertThat(slots).extracting(MovieController.ShowtimeSummary::id).containsExactly(10L);
        MovieController.ShowtimeSummary slot = slots.get(0);
        assertThat(slot.cinemaName()).isEqualTo("Legend Toul Kork");
        assertThat(slot.hasDolbyAtmos()).isTrue();
        assertThat(slot.hasScreenX()).isFalse();
    }

    @Test
    void detail_fallsBackToHallCityWhenTheHallHasNoCinema() {
        Movie movie = new Movie();
        movie.setId(2L);
        movie.setStatus(MovieStatus.NOW_SHOWING);
        Hall hall = new Hall();
        hall.setId(4L);
        hall.setName("Hall 1");
        hall.setCity("Phnom Penh");
        LocalDate future = LocalDate.now().plusDays(1);

        when(movieService.findById(2L)).thenReturn(movie);
        when(showtimeRepository.findByMovieIdWithHall(2L))
                .thenReturn(List.of(showtime(20L, future, false, hall, movie)));

        MovieController.MovieDetailResponse body = controller.detail(2L, null, null, null, null).getBody();

        assertThat(body.showtimesByDate().get(future).get(0).cinemaName()).isEqualTo("Phnom Penh");
    }
}
