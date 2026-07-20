package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.model.SeatHold;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.repository.SeatHoldRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceTest {

    @Mock private SeatHoldRepository seatHoldRepository;
    @Mock private SystemSettingService systemSettingService;
    @Mock private ShowtimeSeatRepository showtimeSeatRepository;
    @Mock private SeatRealtimeService seatRealtimeService;

    @InjectMocks
    private SeatHoldService seatHoldService;

    @Test
    void holdSeatsRealtime_savesHoldsAndPublishes() {
        when(systemSettingService.getInt(SystemSettingService.HOLD_MINUTES, 10)).thenReturn(10);

        Showtime st = new Showtime();
        st.setId(50L);

        ShowtimeSeat s1 = new ShowtimeSeat();
        s1.setId(1L);
        s1.setShowtime(st);
        s1.setStatus(ShowtimeSeatStatus.AVAILABLE);

        ShowtimeSeat s2 = new ShowtimeSeat();
        s2.setId(2L);
        s2.setShowtime(st);
        s2.setStatus(ShowtimeSeatStatus.AVAILABLE);

        when(showtimeSeatRepository.findAllByShowtimeIdAndIdsForUpdate(50L, List.of(1L, 2L)))
                .thenReturn(List.of(s1, s2));
        when(seatHoldRepository.findActiveHoldsByOtherSession(eq(List.of(1L, 2L)), eq("S"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        seatHoldService.holdSeatsRealtime(50L, List.of(1L, 2L), "S");

        verify(seatHoldRepository).deleteBySessionIdAndSeatIds("S", List.of(1L, 2L));
        verify(seatHoldRepository).saveAll(any());
        verify(seatRealtimeService).publishHeld(eq(50L), eq(List.of(1L, 2L)), eq("S"), any(LocalDateTime.class));
    }

    @Test
    void holdSeatsRealtime_rejectsWhenHeldByOther() {
        Showtime st = new Showtime();
        st.setId(50L);

        ShowtimeSeat s1 = new ShowtimeSeat();
        s1.setId(1L);
        s1.setShowtime(st);
        s1.setStatus(ShowtimeSeatStatus.AVAILABLE);

        when(showtimeSeatRepository.findAllByShowtimeIdAndIdsForUpdate(50L, List.of(1L)))
                .thenReturn(List.of(s1));
        when(seatHoldRepository.findActiveHoldsByOtherSession(eq(List.of(1L)), eq("S"), any(LocalDateTime.class)))
                .thenReturn(List.of(new SeatHold(1L, 1L, "OTHER", LocalDateTime.now().plusMinutes(5))));

        assertThrows(IllegalStateException.class, () -> seatHoldService.holdSeatsRealtime(50L, List.of(1L), "S"));

        verify(seatHoldRepository, never()).saveAll(any());
        verify(seatRealtimeService, never()).publishHeld(any(), any(), any(), any());
    }

    @Test
    void holdSeatsRealtime_rejectsWhenAlreadyBooked() {
        Showtime st = new Showtime();
        st.setId(50L);

        ShowtimeSeat s1 = new ShowtimeSeat();
        s1.setId(1L);
        s1.setShowtime(st);
        s1.setStatus(ShowtimeSeatStatus.BOOKED);

        when(showtimeSeatRepository.findAllByShowtimeIdAndIdsForUpdate(50L, List.of(1L)))
                .thenReturn(List.of(s1));

        assertThrows(IllegalStateException.class, () -> seatHoldService.holdSeatsRealtime(50L, List.of(1L), "S"));

        verify(seatHoldRepository, never()).saveAll(any());
        verify(seatRealtimeService, never()).publishHeld(any(), any(), any(), any());
    }

    @Test
    void releaseSeatsRealtime_deletesAndPublishes() {
        seatHoldService.releaseSeatsRealtime(50L, List.of(1L, 2L), "S");
        verify(seatHoldRepository).deleteBySessionIdAndSeatIds("S", List.of(1L, 2L));
        verify(seatRealtimeService).publishReleased(50L, List.of(1L, 2L), "S");
    }

    @Test
    void cleanupExpiredHolds_publishesReleasePerShowtime() {
        SeatHoldRepository.ExpiredHoldRow r1 = new SeatHoldRepository.ExpiredHoldRow() {
            @Override public Long getShowtimeId() { return 10L; }
            @Override public Long getShowtimeSeatId() { return 100L; }
            @Override public String getSessionId() { return "A"; }
        };
        SeatHoldRepository.ExpiredHoldRow r2 = new SeatHoldRepository.ExpiredHoldRow() {
            @Override public Long getShowtimeId() { return 10L; }
            @Override public Long getShowtimeSeatId() { return 101L; }
            @Override public String getSessionId() { return "A"; }
        };
        SeatHoldRepository.ExpiredHoldRow r3 = new SeatHoldRepository.ExpiredHoldRow() {
            @Override public Long getShowtimeId() { return 11L; }
            @Override public Long getShowtimeSeatId() { return 200L; }
            @Override public String getSessionId() { return "B"; }
        };

        when(seatHoldRepository.findExpiredWithShowtimeId(any(LocalDateTime.class)))
                .thenReturn(List.of(r1, r2, r3));

        seatHoldService.cleanupExpiredHolds();

        verify(seatHoldRepository).deleteExpired(any(LocalDateTime.class));
        verify(seatRealtimeService).publishReleased(eq(10L), eq(List.of(100L, 101L)), eq("A"));
        verify(seatRealtimeService).publishReleased(eq(11L), eq(List.of(200L)), eq("B"));
    }
}
