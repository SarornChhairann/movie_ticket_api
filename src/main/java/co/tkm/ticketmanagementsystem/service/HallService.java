package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.repository.HallRepository;
import co.tkm.ticketmanagementsystem.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HallService {

    private static final int COUPLE_SEATS_PER_ROW = 10;
    private static final int NORMAL_SEATS_PER_ROW = 5;
    private static final int TOTAL_SEATS_PER_ROW = COUPLE_SEATS_PER_ROW + NORMAL_SEATS_PER_ROW;
    private static final int DEFAULT_ROWS = 4;

    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public Hall save(Hall hall) {
        if (hall.getTotalSeats() == null || hall.getTotalSeats() <= 0) {
            hall.setTotalSeats(DEFAULT_ROWS * TOTAL_SEATS_PER_ROW);
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

    public Hall findById(Long id) {
        return hallRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hall not found: " + id));
    }

    @Transactional
    public Hall update(Long id, Hall data) {
        Hall hall = findById(id);
        hall.setName(data.getName());
        Integer requestedTotalSeats = data.getTotalSeats();
        if (requestedTotalSeats == null) {
            hall.setTotalSeats(DEFAULT_ROWS * TOTAL_SEATS_PER_ROW);
        } else {
            hall.setTotalSeats(requestedTotalSeats);
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
}
