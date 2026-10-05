package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.Cinema;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.repository.CinemaRepository;
import co.tkm.ticketmanagementsystem.repository.HallRepository;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import co.tkm.ticketmanagementsystem.repository.SeatRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Deletes and saves that used to surface as 500s from database constraints. */
class CatalogDeleteRulesTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Movies {
        @Mock private MovieRepository movieRepository;
        @Mock private ShowtimeRepository showtimeRepository;
        @Mock private MovieBrowseCacheService movieBrowseCacheService;
        @InjectMocks private MovieService movieService;

        @Test
        void deleteIsRefusedWhileShowtimesExist() {
            when(showtimeRepository.countByMovieId(5L)).thenReturn(3L);

            assertThrows(IllegalStateException.class, () -> movieService.delete(5L));
            verify(movieRepository, never()).deleteById(any());
        }

        @Test
        void deleteWithoutShowtimesRemovesAndEvictsBrowseCache() {
            when(showtimeRepository.countByMovieId(5L)).thenReturn(0L);

            movieService.delete(5L);

            verify(movieRepository).deleteById(5L);
            verify(movieBrowseCacheService).evictAll();
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Halls {
        @Mock private HallRepository hallRepository;
        @Mock private SeatRepository seatRepository;
        @Mock private CinemaRepository cinemaRepository;
        @Mock private ShowtimeRepository showtimeRepository;
        @Mock private MovieBrowseCacheService movieBrowseCacheService;
        @InjectMocks private HallService hallService;

        private Hall hall(Long id, String name) {
            Hall h = new Hall();
            h.setId(id);
            h.setName(name);
            return h;
        }

        @Test
        void deleteIsRefusedWhileShowtimesExist() {
            when(hallRepository.findById(9L)).thenReturn(Optional.of(hall(9L, "Hall 1")));
            when(showtimeRepository.countByHallId(9L)).thenReturn(2L);

            assertThrows(IllegalStateException.class, () -> hallService.deleteById(9L));
            verify(hallRepository, never()).delete(any());
        }

        @Test
        void deleteWithoutShowtimesRemovesSeatLayoutThenHall() {
            Hall h = hall(9L, "Hall 1");
            List<Seat> seats = List.of(new Seat(), new Seat());
            when(hallRepository.findById(9L)).thenReturn(Optional.of(h));
            when(showtimeRepository.countByHallId(9L)).thenReturn(0L);
            when(seatRepository.findByHall(h)).thenReturn(seats);

            hallService.deleteById(9L);

            verify(seatRepository).deleteAll(seats);
            verify(hallRepository).delete(h);
        }

        @Test
        void duplicateNameIsA400NotAConstraintFailure() {
            Cinema cinema = new Cinema();
            cinema.setId(UUID.randomUUID());
            when(cinemaRepository.findById(cinema.getId())).thenReturn(Optional.of(cinema));
            when(hallRepository.findByName("Hall 1")).thenReturn(Optional.of(hall(1L, "Hall 1")));

            Hall incoming = hall(null, "Hall 1");
            incoming.setCinema(cinema);

            assertThrows(IllegalArgumentException.class, () -> hallService.save(incoming));
            verify(hallRepository, never()).save(any());
        }

        @Test
        void renamingAHallToItsOwnNameIsAllowed() {
            Cinema cinema = new Cinema();
            cinema.setId(UUID.randomUUID());
            Hall stored = hall(1L, "Hall 1");
            when(hallRepository.findById(1L)).thenReturn(Optional.of(stored));
            when(cinemaRepository.findById(cinema.getId())).thenReturn(Optional.of(cinema));
            when(hallRepository.findByName("Hall 1")).thenReturn(Optional.of(stored));
            when(hallRepository.save(any(Hall.class))).thenAnswer(inv -> inv.getArgument(0));

            Hall edit = hall(null, "Hall 1");
            edit.setCinema(cinema);
            edit.setAmenities("4K");
            edit.setTotalSeats(60);

            Hall saved = hallService.update(1L, edit);
            org.junit.jupiter.api.Assertions.assertEquals("4K", saved.getAmenities());
        }
    }
}
