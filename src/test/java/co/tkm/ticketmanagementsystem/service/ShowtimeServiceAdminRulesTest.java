package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import co.tkm.ticketmanagementsystem.repository.SeatHoldRepository;
import co.tkm.ticketmanagementsystem.repository.SeatRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShowtimeServiceAdminRulesTest {

    @Test
    void deleteById_rejectsUnblockedShowtime() {
        var showtimeRepository = Mockito.mock(ShowtimeRepository.class);
        var movieRepository = Mockito.mock(MovieRepository.class);
        var seatRepository = Mockito.mock(SeatRepository.class);
        var showtimeSeatRepository = Mockito.mock(ShowtimeSeatRepository.class);
        var bookingRepository = Mockito.mock(BookingRepository.class);
        var seatHoldRepository = Mockito.mock(SeatHoldRepository.class);
        var systemSettingService = Mockito.mock(SystemSettingService.class);
        var cache = Mockito.mock(MovieBrowseCacheService.class);

        Showtime st = showtime(1L, false);
        when(showtimeRepository.findById(1L)).thenReturn(Optional.of(st));

        ShowtimeService service = new ShowtimeService(
                showtimeRepository, movieRepository, seatRepository, showtimeSeatRepository, bookingRepository, seatHoldRepository, systemSettingService, cache
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.deleteById(1L));
        assertEquals("Only blocked showtimes can be deleted. Block the showtime first.", ex.getMessage());

        verify(showtimeRepository, never()).deleteById(1L);
        verify(showtimeSeatRepository, never()).deleteByShowtimeId(1L);
        verify(seatHoldRepository, never()).deleteBySeatIds(Mockito.anyList());
    }

    @Test
    void deleteById_rejectsWhenAnyBookingsExist() {
        var showtimeRepository = Mockito.mock(ShowtimeRepository.class);
        var movieRepository = Mockito.mock(MovieRepository.class);
        var seatRepository = Mockito.mock(SeatRepository.class);
        var showtimeSeatRepository = Mockito.mock(ShowtimeSeatRepository.class);
        var bookingRepository = Mockito.mock(BookingRepository.class);
        var seatHoldRepository = Mockito.mock(SeatHoldRepository.class);
        var systemSettingService = Mockito.mock(SystemSettingService.class);
        var cache = Mockito.mock(MovieBrowseCacheService.class);

        Showtime st = showtime(1L, true);
        when(showtimeRepository.findById(1L)).thenReturn(Optional.of(st));
        when(bookingRepository.existsByShowtimeId(1L)).thenReturn(true);
        when(bookingRepository.countByShowtimeId(1L)).thenReturn(2L);

        ShowtimeService service = new ShowtimeService(
                showtimeRepository, movieRepository, seatRepository, showtimeSeatRepository, bookingRepository, seatHoldRepository, systemSettingService, cache
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.deleteById(1L));
        assertEquals("Cannot delete showtime: it has 2 booking(s). Deletion is disabled to preserve booking history.", ex.getMessage());

        verify(showtimeSeatRepository, never()).deleteByShowtimeId(1L);
        verify(showtimeRepository, never()).deleteById(1L);
        verify(cache, never()).evictAll();
    }

    @Test
    void deleteById_deletesShowtimeSeatsAndSeatHoldsFirst() {
        var showtimeRepository = Mockito.mock(ShowtimeRepository.class);
        var movieRepository = Mockito.mock(MovieRepository.class);
        var seatRepository = Mockito.mock(SeatRepository.class);
        var showtimeSeatRepository = Mockito.mock(ShowtimeSeatRepository.class);
        var bookingRepository = Mockito.mock(BookingRepository.class);
        var seatHoldRepository = Mockito.mock(SeatHoldRepository.class);
        var systemSettingService = Mockito.mock(SystemSettingService.class);
        var cache = Mockito.mock(MovieBrowseCacheService.class);

        Showtime st = showtime(1L, true);
        when(showtimeRepository.findById(1L)).thenReturn(Optional.of(st));
        when(bookingRepository.existsByShowtimeId(1L)).thenReturn(false);
        when(showtimeSeatRepository.findIdsByShowtimeId(1L)).thenReturn(List.of(10L, 11L));

        ShowtimeService service = new ShowtimeService(
                showtimeRepository, movieRepository, seatRepository, showtimeSeatRepository, bookingRepository, seatHoldRepository, systemSettingService, cache
        );

        service.deleteById(1L);

        verify(seatHoldRepository).deleteBySeatIds(List.of(10L, 11L));
        verify(showtimeSeatRepository).deleteByShowtimeId(1L);
        verify(showtimeRepository).deleteById(1L);
        verify(cache).evictAll();
    }

    @Test
    void toggleBlocked_rejectsBlockingWhenActiveBookingsExist() {
        var showtimeRepository = Mockito.mock(ShowtimeRepository.class);
        var movieRepository = Mockito.mock(MovieRepository.class);
        var seatRepository = Mockito.mock(SeatRepository.class);
        var showtimeSeatRepository = Mockito.mock(ShowtimeSeatRepository.class);
        var bookingRepository = Mockito.mock(BookingRepository.class);
        var seatHoldRepository = Mockito.mock(SeatHoldRepository.class);
        var systemSettingService = Mockito.mock(SystemSettingService.class);
        var cache = Mockito.mock(MovieBrowseCacheService.class);

        Showtime st = showtime(7L, false);
        when(showtimeRepository.findById(7L)).thenReturn(Optional.of(st));
        when(bookingRepository.existsByShowtimeIdAndStatusNot(7L, BookingStatus.CANCELLED)).thenReturn(true);

        ShowtimeService service = new ShowtimeService(
                showtimeRepository, movieRepository, seatRepository, showtimeSeatRepository, bookingRepository, seatHoldRepository, systemSettingService, cache
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.toggleBlocked(7L));
        assertEquals("Cannot block a showtime that has active bookings. Cancel bookings first.", ex.getMessage());

        verify(showtimeRepository, never()).save(Mockito.any());
        verify(cache, never()).evictAll();
    }

    @Test
    void toggleBlocked_rejectsBlockingWhenWithinEditLockWindow() {
        var showtimeRepository = Mockito.mock(ShowtimeRepository.class);
        var movieRepository = Mockito.mock(MovieRepository.class);
        var seatRepository = Mockito.mock(SeatRepository.class);
        var showtimeSeatRepository = Mockito.mock(ShowtimeSeatRepository.class);
        var bookingRepository = Mockito.mock(BookingRepository.class);
        var seatHoldRepository = Mockito.mock(SeatHoldRepository.class);
        var systemSettingService = Mockito.mock(SystemSettingService.class);
        var cache = Mockito.mock(MovieBrowseCacheService.class);

        when(systemSettingService.getInt(SystemSettingService.CANCELLATION_HOURS, 2)).thenReturn(2);

        Showtime st = showtime(8L, false);
        LocalDateTime near = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh")).plusHours(1);
        st.setShowDate(near.toLocalDate());
        st.setShowTime(near.toLocalTime().withSecond(0).withNano(0));

        when(showtimeRepository.findById(8L)).thenReturn(Optional.of(st));
        when(bookingRepository.existsByShowtimeIdAndStatusNot(8L, BookingStatus.CANCELLED)).thenReturn(false);

        ShowtimeService service = new ShowtimeService(
                showtimeRepository, movieRepository, seatRepository, showtimeSeatRepository, bookingRepository, seatHoldRepository, systemSettingService, cache
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.toggleBlocked(8L));
        assertEquals("Cannot block a showtime within 2 hour(s) of start time.", ex.getMessage());
    }

    @Test
    void toggleBlocked_rejectsUnblockingPastShowtime() {
        var showtimeRepository = Mockito.mock(ShowtimeRepository.class);
        var movieRepository = Mockito.mock(MovieRepository.class);
        var seatRepository = Mockito.mock(SeatRepository.class);
        var showtimeSeatRepository = Mockito.mock(ShowtimeSeatRepository.class);
        var bookingRepository = Mockito.mock(BookingRepository.class);
        var seatHoldRepository = Mockito.mock(SeatHoldRepository.class);
        var systemSettingService = Mockito.mock(SystemSettingService.class);
        var cache = Mockito.mock(MovieBrowseCacheService.class);

        Showtime st = showtime(9L, true);
        st.setShowDate(LocalDate.now().minusDays(2));
        st.setShowTime(LocalTime.NOON);

        when(showtimeRepository.findById(9L)).thenReturn(Optional.of(st));
        when(bookingRepository.existsByShowtimeIdAndStatusNot(9L, BookingStatus.CANCELLED)).thenReturn(false);

        ShowtimeService service = new ShowtimeService(
                showtimeRepository, movieRepository, seatRepository, showtimeSeatRepository, bookingRepository, seatHoldRepository, systemSettingService, cache
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.toggleBlocked(9L));
        assertEquals("Cannot publish a showtime in the past.", ex.getMessage());

        verify(showtimeRepository, never()).save(Mockito.any());
    }

    @Test
    void save_rejectsEditWhenAnyBookingsExist() {
        var showtimeRepository = Mockito.mock(ShowtimeRepository.class);
        var movieRepository = Mockito.mock(MovieRepository.class);
        var seatRepository = Mockito.mock(SeatRepository.class);
        var showtimeSeatRepository = Mockito.mock(ShowtimeSeatRepository.class);
        var bookingRepository = Mockito.mock(BookingRepository.class);
        var seatHoldRepository = Mockito.mock(SeatHoldRepository.class);
        var systemSettingService = Mockito.mock(SystemSettingService.class);
        var cache = Mockito.mock(MovieBrowseCacheService.class);

        Showtime existing = showtime(5L, false);
        when(showtimeRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(bookingRepository.existsByShowtimeId(5L)).thenReturn(true);

        Showtime update = showtime(5L, false);
        update.setShowTime(LocalTime.of(20, 0));

        ShowtimeService service = new ShowtimeService(
                showtimeRepository, movieRepository, seatRepository, showtimeSeatRepository, bookingRepository, seatHoldRepository, systemSettingService, cache
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.save(update));
        assertEquals("Cannot edit showtime because bookings already exist.", ex.getMessage());
    }

    @Test
    void save_rejectsEditWhenWithinEditLockWindow() {
        var showtimeRepository = Mockito.mock(ShowtimeRepository.class);
        var movieRepository = Mockito.mock(MovieRepository.class);
        var seatRepository = Mockito.mock(SeatRepository.class);
        var showtimeSeatRepository = Mockito.mock(ShowtimeSeatRepository.class);
        var bookingRepository = Mockito.mock(BookingRepository.class);
        var seatHoldRepository = Mockito.mock(SeatHoldRepository.class);
        var systemSettingService = Mockito.mock(SystemSettingService.class);
        var cache = Mockito.mock(MovieBrowseCacheService.class);

        when(systemSettingService.getInt(SystemSettingService.CANCELLATION_HOURS, 2)).thenReturn(2);

        Showtime existing = showtime(6L, false);
        LocalDateTime near = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh")).plusHours(1);
        existing.setShowDate(near.toLocalDate());
        existing.setShowTime(near.toLocalTime().withSecond(0).withNano(0));
        when(showtimeRepository.findById(6L)).thenReturn(Optional.of(existing));
        when(bookingRepository.existsByShowtimeId(6L)).thenReturn(false);

        Showtime update = showtime(6L, false);
        update.setShowDate(existing.getShowDate());
        update.setShowTime(existing.getShowTime());

        ShowtimeService service = new ShowtimeService(
                showtimeRepository, movieRepository, seatRepository, showtimeSeatRepository, bookingRepository, seatHoldRepository, systemSettingService, cache
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.save(update));
        assertEquals("Cannot edit showtime within 2 hour(s) of start time.", ex.getMessage());
    }

    private static Showtime showtime(Long id, boolean blocked) {
        Movie movie = new Movie();
        movie.setId(100L);
        movie.setDurationMinutes(120);

        Hall hall = new Hall();
        hall.setId(200L);

        Showtime st = new Showtime();
        st.setId(id);
        st.setMovie(movie);
        st.setHall(hall);
        st.setShowDate(LocalDate.now().plusDays(3));
        st.setShowTime(LocalTime.of(18, 0));
        st.setNormalPrice(new BigDecimal("5.00"));
        st.setCouplePrice(new BigDecimal("8.00"));
        st.setBlocked(blocked);
        return st;
    }
}
