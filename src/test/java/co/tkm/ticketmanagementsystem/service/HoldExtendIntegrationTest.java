package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.controller.HoldSessions;
import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.model.SeatHold;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.repository.HallRepository;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import co.tkm.ticketmanagementsystem.repository.SeatHoldRepository;
import co.tkm.ticketmanagementsystem.repository.SeatRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 14 against H2: one extend per hold (G32), and holding again keeps the running
 * clock so the timer cannot be reset by re-holding.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HoldExtendIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SeatHoldService seatHoldService;
    @Autowired private SeatHoldRepository seatHoldRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private HallRepository hallRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private ShowtimeRepository showtimeRepository;
    @Autowired private ShowtimeSeatRepository showtimeSeatRepository;

    @MockitoBean private SeatRealtimeService seatRealtimeService;

    private String session;
    private Long showtimeId;
    private Map<String, Long> seats;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        session = UUID.randomUUID().toString();

        Movie movie = new Movie();
        movie.setTitle("Extend IT " + suffix);
        movie.setGenre("Drama");
        movie.setDurationMinutes(120);
        movie.setStatus(MovieStatus.NOW_SHOWING);
        movie.setReleaseDate(LocalDate.now());
        movie = movieRepository.save(movie);

        Hall hall = new Hall();
        hall.setName("Extend Hall " + suffix);
        hall.setCity("Phnom Penh");
        hall.setCapacity(3);
        hall = hallRepository.save(hall);

        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setHall(hall);
        showtime.setShowDate(LocalDate.now().plusDays(1));
        showtime.setShowTime(LocalTime.of(19, 0));
        showtime.setNormalPrice(new BigDecimal("5.00"));
        showtime.setCouplePrice(new BigDecimal("8.00"));
        showtimeId = showtimeRepository.save(showtime).getId();

        seats = new LinkedHashMap<>();
        for (String code : List.of("N1", "N2", "N3")) {
            Seat seat = new Seat();
            seat.setHall(hall);
            seat.setSeatCode(code);
            seat.setSeatType(SeatType.NORMAL);
            seat.setRowNumber(1);
            seat = seatRepository.save(seat);
            ShowtimeSeat ss = new ShowtimeSeat();
            ss.setShowtime(showtime);
            ss.setSeat(seat);
            ss.setStatus(ShowtimeSeatStatus.AVAILABLE);
            seats.put(code, showtimeSeatRepository.save(ss).getId());
        }
    }

    private List<Long> ids(String... codes) {
        return java.util.Arrays.stream(codes).map(seats::get).toList();
    }

    private void assertConflict(String code, org.junit.jupiter.api.function.Executable call) {
        ApiException ex = assertThrows(ApiException.class, call);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(code);
    }

    @Test
    void extendAddsTheSettingOnceAndBroadcastsTheNewExpiry() {
        SeatHoldService.HoldResult held = seatHoldService.holdSeatsRealtime(showtimeId, ids("N1", "N2"), session);

        SeatHoldService.HoldResult extended = seatHoldService.extend(showtimeId, session);

        assertThat(Duration.between(held.expiresAt(), extended.expiresAt()))
                .isEqualTo(Duration.ofMinutes(SeatHoldService.DEFAULT_HOLD_EXTEND_MINUTES));
        assertThat(extended.extended()).isTrue();
        assertThat(seatHoldRepository.findBySessionId(session))
                .hasSize(2)
                .allSatisfy(h -> {
                    assertThat(h.isExtended()).isTrue();
                    assertThat(h.getExpiresAt()).isEqualTo(extended.expiresAt());
                });
        verify(seatRealtimeService).publishHeld(showtimeId, ids("N1", "N2"), session, extended.expiresAt());

        assertConflict(SeatHoldService.HOLD_EXTEND_LIMIT, () -> seatHoldService.extend(showtimeId, session));
    }

    @Test
    void nothingHeldOrAnExpiredHoldIsHoldExpired() {
        assertConflict(SeatHoldService.HOLD_EXPIRED, () -> seatHoldService.extend(showtimeId, session));

        seatHoldService.holdSeatsRealtime(showtimeId, ids("N1"), session);
        for (SeatHold hold : seatHoldRepository.findBySessionId(session)) {
            hold.setExpiresAt(LocalDateTime.now().minusSeconds(1));
            seatHoldRepository.save(hold);
        }

        assertConflict(SeatHoldService.HOLD_EXPIRED, () -> seatHoldService.extend(showtimeId, session));
    }

    @Test
    void holdingAgainKeepsTheRunningClockAndTheExtendedFlag() {
        SeatHoldService.HoldResult first = seatHoldService.holdSeatsRealtime(showtimeId, ids("N1"), session);

        SeatHoldService.HoldResult again = seatHoldService.holdSeatsRealtime(showtimeId, ids("N1", "N2"), session);
        assertThat(again.expiresAt()).isEqualTo(first.expiresAt());
        assertThat(again.extended()).isFalse();

        SeatHoldService.HoldResult extended = seatHoldService.extend(showtimeId, session);
        SeatHoldService.HoldResult afterExtend = seatHoldService.holdSeatsRealtime(showtimeId, ids("N2", "N3"), session);

        assertThat(afterExtend.expiresAt()).isEqualTo(extended.expiresAt());
        assertThat(afterExtend.extended()).isTrue();
        assertConflict(SeatHoldService.HOLD_EXTEND_LIMIT, () -> seatHoldService.extend(showtimeId, session));
    }

    @Test
    void extendOverHttpAnswersLikeHold() throws Exception {
        seatHoldService.holdSeatsRealtime(showtimeId, ids("N1"), session);
        String body = objectMapper.writeValueAsString(Map.of("showtimeId", showtimeId, "seatIds", ids("N1")));

        String response = mvc.perform(post("/api/seat-holds/extend")
                        .header(HoldSessions.HEADER, session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("extended").asBoolean()).isTrue();
        assertThat(json.get("expiresAtEpochMs").asLong()).isGreaterThan(System.currentTimeMillis() + 10 * 60_000L);

        mvc.perform(post("/api/seat-holds/extend")
                        .header(HoldSessions.HEADER, session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }
}
