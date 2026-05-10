package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.Cinema;
import co.tkm.ticketmanagementsystem.repository.CinemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CinemaService {

    private final CinemaRepository cinemaRepository;

    @Transactional
    public Cinema save(Cinema cinema) {
        if (cinema == null) {
            throw new IllegalArgumentException("Cinema is required.");
        }
        if (cinema.getName() == null || cinema.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Cinema name is required.");
        }
        cinema.setName(cinema.getName().trim());
        if (cinema.getAddress() != null) {
            cinema.setAddress(cinema.getAddress().trim());
        }
        if (cinema.getOpeningHours() != null) {
            cinema.setOpeningHours(cinema.getOpeningHours().trim());
        }
        return cinemaRepository.save(cinema);
    }

    @Transactional
    public void deleteById(UUID id) {
        cinemaRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Cinema> findAll() {
        return cinemaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Cinema findById(UUID id) {
        return cinemaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cinema not found: " + id));
    }

    @Transactional(readOnly = true)
    public long count() {
        return cinemaRepository.count();
    }
}
