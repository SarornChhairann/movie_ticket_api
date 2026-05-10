package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.model.Cinema;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.repository.CinemaRepository;
import co.tkm.ticketmanagementsystem.repository.HallRepository;
import co.tkm.ticketmanagementsystem.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HallService {

    private static final int COUPLE_SEATS_PER_ROW = 10;
    private static final int NORMAL_SEATS_PER_ROW = 5;
    private static final int TOTAL_SEATS_PER_ROW = COUPLE_SEATS_PER_ROW + NORMAL_SEATS_PER_ROW;
    private static final int DEFAULT_ROWS = 4;

    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;
    private final CinemaRepository cinemaRepository;

    @Transactional
    public Hall save(Hall hall) {
        if (hall.getCinema() == null || hall.getCinema().getId() == null) {
            throw new IllegalArgumentException("Cinema is required.");
        }
        Cinema cinema = cinemaRepository.findById(hall.getCinema().getId())
                .orElseThrow(() -> new IllegalArgumentException("Cinema not found: " + hall.getCinema().getId()));
        hall.setCinema(cinema);

        if (hall.getTotalSeats() == null || hall.getTotalSeats() <= 0) {
            hall.setTotalSeats(DEFAULT_ROWS * TOTAL_SEATS_PER_ROW);
        }
        if (hall.getCapacity() == null) {
            hall.setCapacity(hall.getTotalSeats());
        }
        Hall saved = hallRepository.save(hall);
        if (seatRepository.findByHall(saved).isEmpty()) {
            seatRepository.saveAll(generateSeats(saved));
        }
        return saved;
    }

    private List<Seat> generateSeats(Hall hall) {
        List<Seat> seats = new ArrayList<>();
        int rows = Math.max(1, (int) Math.ceil(hall.getTotalSeats() / (double) TOTAL_SEATS_PER_ROW));
        for (int row = 1; row <= rows; row++) {
            String rowLetter = String.valueOf((char) ('A' + row - 1));
            for (int number = 1; number <= TOTAL_SEATS_PER_ROW; number++) {
                Seat seat = new Seat();
                seat.setHall(hall);
                seat.setRowNumber(row);
                seat.setSeatCode(rowLetter + number);
                seat.setSeatType(number <= COUPLE_SEATS_PER_ROW ? SeatType.COUPLE : SeatType.NORMAL);
                seats.add(seat);
            }
        }
        return seats;
    }

    public List<Hall> findAll() {
        return hallRepository.findAll();
    }

    public List<String> findDistinctCities() {
        return hallRepository.findDistinctCities();
    }

    public List<Hall> findByCity(String city) {
        if (city == null || city.isBlank()) return hallRepository.findAll();
        return hallRepository.findByCityIgnoreCase(city);
    }

    public Hall findById(Long id) {
        return hallRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hall not found: " + id));
    }

    @Transactional
    public Hall update(Long id, Hall data) {
        Hall hall = findById(id);
        if (data.getCinema() == null || data.getCinema().getId() == null) {
            throw new IllegalArgumentException("Cinema is required.");
        }
        Cinema cinema = cinemaRepository.findById(data.getCinema().getId())
                .orElseThrow(() -> new IllegalArgumentException("Cinema not found: " + data.getCinema().getId()));
        hall.setCinema(cinema);
        hall.setName(data.getName());
        hall.setCity(data.getCity());
        hall.setAddress(data.getAddress());
        hall.setType(data.getType());
        hall.setHasDolbyAtmos(data.isHasDolbyAtmos());
        hall.setHasScreenX(data.isHasScreenX());
        Integer requestedTotalSeats = data.getTotalSeats();
        if (requestedTotalSeats == null) {
            hall.setTotalSeats(DEFAULT_ROWS * TOTAL_SEATS_PER_ROW);
        } else {
            hall.setTotalSeats(requestedTotalSeats);
        }
        if (data.getCapacity() == null) {
            hall.setCapacity(hall.getTotalSeats());
        } else {
            hall.setCapacity(data.getCapacity());
        }
        return hallRepository.save(hall);
    }

    @Transactional
    public void deleteById(Long id) {
        hallRepository.deleteById(id);
    }

    public long count() {
        return hallRepository.count();
    }

    public long countByCinemaId(UUID cinemaId) {
        if (cinemaId == null) return 0;
        return hallRepository.countByCinemaId(cinemaId);
    }
}
