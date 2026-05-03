package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import co.tkm.ticketmanagementsystem.repository.SeatRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowtimeService {

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;

    @Transactional
    public Showtime save(Showtime showtime) {
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
        showtimeRepository.deleteById(id);
    }

    public long count() {
        return showtimeRepository.count();
    }

    @Transactional
    public void toggleBlocked(Long id) {
        Showtime s = showtimeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found: " + id));
        s.setBlocked(!s.isBlocked());
        showtimeRepository.save(s);
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
        return save(copy);
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
