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
}
