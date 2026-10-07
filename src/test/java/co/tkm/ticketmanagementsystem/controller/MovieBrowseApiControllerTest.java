package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.controller.MovieBrowseApiController.MovieBrowseResponse;
import co.tkm.ticketmanagementsystem.controller.MovieBrowseApiController.ShowtimeChip;
import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Cinema;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.service.MovieBrowseCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** G34: Home's "Showing today" rail. */
@ExtendWith(MockitoExtension.class)
class MovieBrowseApiControllerTest {

    @Mock private ShowtimeRepository showtimeRepository;
    @Mock private MovieRepository movieRepository;

    private static Movie movie() {
        Movie movie = new Movie();
        movie.setId(1L);
        movie.setTitle("Dune");
        movie.setGenre("Sci-Fi");
        movie.setDurationMinutes(155);
        movie.setStatus(MovieStatus.NOW_SHOWING);
        return movie;
    }

    private static Showtime showtime(long id, LocalDate date, LocalTime time, Movie movie) {
        Cinema cinema = new Cinema();
        cinema.setName("Legend Toul Kork");
        Hall hall = new Hall();
        hall.setId(3L);
        hall.setName("Hall 3");
        hall.setCinema(cinema);
        hall.setHasDolbyAtmos(true);
        Showtime s = new Showtime();
        s.setId(id);
        s.setMovie(movie);
        s.setHall(hall);
        s.setShowDate(date);
        s.setShowTime(time);
        s.setNormalPrice(new BigDecimal("6.00"));
        s.setCouplePrice(new BigDecimal("12.00"));
        return s;
    }

    @Test
    void chipCarriesHallCinemaAndFlags_andMovieCarriesGenreAndDuration() {
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Phnom_Penh")).plusDays(1);
        when(showtimeRepository.findByShowDateCityNotBlocked(eq(tomorrow), any()))
                .thenReturn(List.of(showtime(10L, tomorrow, LocalTime.of(19, 30), movie())));
        var controller = new MovieBrowseApiController(showtimeRepository, movieRepository, new MovieBrowseCacheService());

        List<MovieBrowseResponse> body = controller.browse("now_showing", tomorrow, null, null, null).getBody();

        assertThat(body).hasSize(1);
        assertThat(body.get(0).genre()).isEqualTo("Sci-Fi");
        assertThat(body.get(0).durationMinutes()).isEqualTo(155);
        ShowtimeChip chip = body.get(0).showtimes().get(0);
        assertThat(chip.showtimeId()).isEqualTo(10L);
        assertThat(chip.time()).isEqualTo("19:30");
        assertThat(chip.showDate()).isEqualTo(tomorrow.toString());
        assertThat(chip.hallName()).isEqualTo("Hall 3");
        assertThat(chip.cinemaName()).isEqualTo("Legend Toul Kork");
        assertThat(chip.hasDolbyAtmos()).isTrue();
        assertThat(chip.hasScreenX()).isFalse();
    }

    @Test
    void startedShowtimesAreDroppedEvenWhenTheDayIsCached() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Phnom_Penh"));
        Movie movie = movie();
        // 00:00 has started unless it is exactly midnight; 23:59 has not unless it is 23:59.
        when(showtimeRepository.findByShowDateCityNotBlocked(eq(today), any())).thenReturn(List.of(
                showtime(10L, today, LocalTime.MIDNIGHT, movie),
                showtime(11L, today, LocalTime.of(23, 59), movie)));
        var controller = new MovieBrowseApiController(showtimeRepository, movieRepository, new MovieBrowseCacheService());

        controller.browse("now_showing", today, null, null, null);
        List<MovieBrowseResponse> cached = controller.browse("now_showing", today, null, null, null).getBody();

        verify(showtimeRepository, times(1)).findByShowDateCityNotBlocked(eq(today), any());
        assertThat(cached.get(0).showtimes()).extracting(ShowtimeChip::showtimeId).containsExactly(11L);
    }

    @Test
    void upcoming_capsAtFourAfterDroppingStarted_andDropsMoviesWithNone() {
        LocalDate day = LocalDate.of(2026, 10, 7);
        List<ShowtimeChip> chips = List.of("10:00", "12:00", "14:00", "16:00", "18:00", "20:00").stream()
                .map(t -> new ShowtimeChip((long) t.hashCode(), t, day.toString(), "Hall 1", "Cinema", false, false))
                .toList();
        var withTimes = new MovieBrowseResponse(1L, "A", "", "", "", "", "", "", "NOW_SHOWING", "", chips, "", null);
        var allStarted = new MovieBrowseResponse(2L, "B", "", "", "", "", "", "", "NOW_SHOWING", "",
                chips.subList(0, 1), "", null);

        var out = MovieBrowseApiController.upcoming(List.of(withTimes, allStarted), LocalDateTime.of(day, LocalTime.of(11, 0)));

        assertThat(out).extracting(MovieBrowseResponse::id).containsExactly(1L);
        assertThat(out.get(0).showtimes()).extracting(ShowtimeChip::time)
                .containsExactly("12:00", "14:00", "16:00", "18:00");
    }
}
