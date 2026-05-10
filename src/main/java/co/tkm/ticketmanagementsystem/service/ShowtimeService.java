package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import co.tkm.ticketmanagementsystem.repository.SeatRepository;
import co.tkm.ticketmanagementsystem.repository.SeatHoldRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShowtimeService {

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final BookingRepository bookingRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final SystemSettingService systemSettingService;
    private final MovieBrowseCacheService movieBrowseCacheService;
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Phnom_Penh");

    @Transactional
    public Showtime save(Showtime showtime) {
        if (showtime.getId() != null) {
            Showtime existing = showtimeRepository.findById(showtime.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Showtime not found: " + showtime.getId()));
            if (bookingRepository.existsByShowtimeId(showtime.getId())) {
                throw new IllegalStateException("Cannot edit showtime because bookings already exist.");
            }
            if (isWithinEditLockWindow(existing)) {
                int hours = getEditLockHours();
                throw new IllegalStateException("Cannot edit showtime within " + hours + " hour(s) of start time.");
            }
        }
        validateNoOverlap(
                showtime.getHall().getId(),
                showtime.getShowDate(),
                showtime.getShowTime(),
                showtime.getMovie().getDurationMinutes(),
                showtime.getId()
        );
        Showtime saved = showtimeRepository.save(showtime);
        if (showtimeSeatRepository.findByShowtime(saved).isEmpty()) {
            List<Seat> seats = seatRepository.findByHall(saved.getHall());
            List<ShowtimeSeat> showtimeSeats = new ArrayList<>();
            for (Seat seat : seats) {
                ShowtimeSeat showtimeSeat = new ShowtimeSeat();
                showtimeSeat.setShowtime(saved);
                showtimeSeat.setSeat(seat);
                showtimeSeat.setStatus(ShowtimeSeatStatus.AVAILABLE);
                showtimeSeats.add(showtimeSeat);
            }
            showtimeSeatRepository.saveAll(showtimeSeats);
        }
        movieBrowseCacheService.evictAll();
        return saved;
    }

    public List<Showtime> findByMovieId(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new IllegalArgumentException("Movie not found: " + movieId));
        return showtimeRepository.findByMovie(movie);
    }

    @Transactional(readOnly = true)
    public List<Showtime> findAll() {
        List<Showtime> showtimes = showtimeRepository.findAll();
        for (Showtime showtime : showtimes) {
            showtime.getMovie().getTitle();
            showtime.getHall().getName();
        }
        return showtimes;
    }

    @Transactional
    public void deleteById(Long id) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found: " + id));

        if (!showtime.isBlocked()) {
            throw new IllegalStateException("Only blocked showtimes can be deleted. Block the showtime first.");
        }

        boolean hasAnyBookings = bookingRepository.existsByShowtimeId(id);
        if (hasAnyBookings) {
            long total = bookingRepository.countByShowtimeId(id);
            throw new IllegalStateException("Cannot delete showtime: it has " + total + " booking(s). Deletion is disabled to preserve booking history.");
        }

        List<Long> showtimeSeatIds = showtimeSeatRepository.findIdsByShowtimeId(id);
        if (!showtimeSeatIds.isEmpty()) {
            seatHoldRepository.deleteBySeatIds(showtimeSeatIds);
        }
        showtimeSeatRepository.deleteByShowtimeId(id);
        showtimeRepository.deleteById(id);
        movieBrowseCacheService.evictAll();
    }

    public long count() {
        return showtimeRepository.count();
    }

    @Transactional
    public void toggleBlocked(Long id) {
        Showtime s = showtimeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found: " + id));
        boolean targetBlocked = !s.isBlocked();
        if (targetBlocked) {
            boolean hasActiveBookings = bookingRepository.existsByShowtimeIdAndStatusNot(id, BookingStatus.CANCELLED);
            if (hasActiveBookings) {
                throw new IllegalStateException("Cannot block a showtime that has active bookings. Cancel bookings first.");
            }
            if (isWithinEditLockWindow(s)) {
                int hours = getEditLockHours();
                throw new IllegalStateException("Cannot block a showtime within " + hours + " hour(s) of start time.");
            }
        } else {
            LocalDateTime showDateTime = LocalDateTime.of(s.getShowDate(), s.getShowTime());
            if (showDateTime.isBefore(LocalDateTime.now(APP_ZONE))) {
                throw new IllegalStateException("Cannot publish a showtime in the past.");
            }
            if (isWithinEditLockWindow(s)) {
                int hours = getEditLockHours();
                throw new IllegalStateException("Cannot publish a showtime within " + hours + " hour(s) of start time.");
            }
        }
        s.setBlocked(targetBlocked);
        showtimeRepository.save(s);
        movieBrowseCacheService.evictAll();
    }

    public boolean isWithinEditLockWindow(Showtime showtime) {
        if (showtime == null || showtime.getShowDate() == null || showtime.getShowTime() == null) return false;
        int hours = getEditLockHours();
        LocalDateTime start = LocalDateTime.of(showtime.getShowDate(), showtime.getShowTime());
        return !start.isAfter(LocalDateTime.now(APP_ZONE).plusHours(hours));
    }

    private int getEditLockHours() {
        int hours = systemSettingService.getInt(SystemSettingService.CANCELLATION_HOURS, 2);
        if (hours < 0) return 0;
        return Math.min(hours, 72);
    }

    @Transactional
    public Showtime copyToNextWeek(Long id) {
        Showtime original = showtimeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found: " + id));
        Showtime copy = new Showtime();
        copy.setMovie(original.getMovie());
        copy.setHall(original.getHall());
        copy.setShowDate(original.getShowDate().plusWeeks(1));
        copy.setShowTime(original.getShowTime());
        copy.setNormalPrice(original.getNormalPrice());
        copy.setCouplePrice(original.getCouplePrice());
        copy.setBlocked(false);
        Showtime saved = save(copy);
        movieBrowseCacheService.evictAll();
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Showtime> findByWeek(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        return showtimeRepository.findByShowDateBetweenOrderByShowDateAscShowTimeAsc(weekStart, weekEnd);
    }

    @Transactional(readOnly = true)
    public Showtime findById(Long id) {
        return showtimeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Showtime> findForMovieDateCity(Long movieId, LocalDate showDate, String city) {
        String c = (city == null) ? "" : city.trim();
        return showtimeRepository.findForMovieDateCity(movieId, showDate, c);
    }

    @Transactional(readOnly = true)
    public List<Long> findAvailableMovieIds(LocalDate showDate, String city) {
        String c = (city == null) ? "" : city.trim();
        return showtimeRepository.findAvailableMovieIds(showDate, c);
    }

    @Transactional(readOnly = true)
    public List<Showtime> findForMovieDateCinema(Long movieId, LocalDate showDate, UUID cinemaId) {
        return showtimeRepository.findForMovieDateCinema(movieId, showDate, cinemaId);
    }

    @Transactional(readOnly = true)
    public List<Long> findAvailableMovieIdsByCinema(LocalDate showDate, UUID cinemaId) {
        return showtimeRepository.findAvailableMovieIdsByCinema(showDate, cinemaId);
    }

    /**
     * Throws IllegalArgumentException if the given hall/date/time overlaps with any
     * existing showtime in the same hall (accounting for each movie's duration).
     *
     * @param hallId            hall to check
     * @param showDate          date of the new showtime
     * @param showTime          start time of the new showtime
     * @param newDurationMin    duration of the new movie in minutes
     * @param excludeId         showtime id to exclude from check (for edits), or null
     */
    @Transactional(readOnly = true)
    public void validateNoOverlap(Long hallId, LocalDate showDate, LocalTime showTime,
                                  int newDurationMin, Long excludeId) {
        List<Showtime> existing = showtimeRepository.findByHallIdAndShowDateWithMovie(hallId, showDate);
        LocalTime newEnd = showTime.plusMinutes(newDurationMin);

        for (Showtime s : existing) {
            if (excludeId != null && s.getId().equals(excludeId)) continue;
            LocalTime existEnd = s.getShowTime().plusMinutes(s.getMovie().getDurationMinutes());
            // overlap: newStart < existEnd AND newEnd > existStart
            if (showTime.isBefore(existEnd) && newEnd.isAfter(s.getShowTime())) {
                throw new IllegalArgumentException(
                        "Time conflict: hall is already occupied by \"" + s.getMovie().getTitle()
                        + "\" from " + s.getShowTime() + " to " + existEnd + ".");
            }
        }
    }
}
