package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.HallRepository;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Sprint 13 against H2: every booking gets a reference (G29), and a guest finds it again
 * with reference + phone over HTTP, anonymously, with misses throttled per client (G30).
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingLookupIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingReferenceBackfill backfill;
    @Autowired private MovieRepository movieRepository;
    @Autowired private HallRepository hallRepository;
    @Autowired private ShowtimeRepository showtimeRepository;

    private Booking booking;
    /** One client address per test, so the in-memory limiter never leaks between tests. */
    private String ip;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ip = "10.13." + (suffix.charAt(0) % 200) + "." + (suffix.charAt(1) % 200);

        Movie movie = new Movie();
        movie.setTitle("Lookup IT " + suffix);
        movie.setGenre("Drama");
        movie.setDurationMinutes(120);
        movie.setStatus(MovieStatus.NOW_SHOWING);
        movie.setReleaseDate(LocalDate.now());
        movie = movieRepository.save(movie);

        Hall hall = new Hall();
        hall.setName("Lookup Hall " + suffix);
        hall.setCity("Phnom Penh");
        hall.setCapacity(10);
        hall = hallRepository.save(hall);

        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setHall(hall);
        showtime.setShowDate(LocalDate.now().plusDays(2));
        showtime.setShowTime(LocalTime.of(19, 0));
        showtime.setNormalPrice(new BigDecimal("5.00"));
        showtime.setCouplePrice(new BigDecimal("8.00"));
        showtime = showtimeRepository.save(showtime);

        booking = new Booking();
        booking.setGuestName("Dara");
        booking.setGuestPhone("012 345 678");
        booking.setShowtime(showtime);
        booking.setTotalAmount(new BigDecimal("12.50"));
        booking.setPaymentMethod("ABA_KHQR");
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setBookedAt(LocalDateTime.now());
        booking = bookingRepository.save(booking);
    }

    private MockHttpServletResponse lookup(String reference, String phone) throws Exception {
        return mvc.perform(post("/bookings/lookup")
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reference", reference, "phone", phone))))
                .andReturn().getResponse();
    }

    private String code(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString()).get("code").asText();
    }

    @Test
    void aNewBookingGetsAReferenceOnInsert() {
        assertThat(booking.getReference()).matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}");
    }

    @Test
    void backfillGivesOlderBookingsAReference() {
        booking.setReference(null);
        bookingRepository.save(booking);

        backfill.backfill();

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getReference()).hasSize(8);
    }

    @Test
    void aGuestFindsTheBookingWithAnyFormattingOfReferenceAndPhone() throws Exception {
        String typed = booking.getReference().substring(0, 4).toLowerCase() + "-" + booking.getReference().substring(4);

        MockHttpServletResponse response = lookup(typed, "+855 12-345-678");

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.get("id").asLong()).isEqualTo(booking.getId());
        assertThat(json.get("reference").asText()).isEqualTo(booking.getReference());
    }

    @Test
    void aWrongPhoneAndAWrongReferenceAreTheSame404() throws Exception {
        MockHttpServletResponse wrongPhone = lookup(booking.getReference(), "012 999 999");
        MockHttpServletResponse wrongRef = lookup("ZZZZZZZZ", "012 345 678");

        assertThat(wrongPhone.getStatus()).isEqualTo(404);
        assertThat(code(wrongPhone)).isEqualTo(BookingAccess.BOOKING_NOT_FOUND);
        assertThat(wrongRef.getStatus()).isEqualTo(404);
        assertThat(code(wrongRef)).isEqualTo(BookingAccess.BOOKING_NOT_FOUND);
    }

    @Test
    void fiveMissesBlockTheClientEvenForTheRightAnswer() throws Exception {
        for (int i = 0; i < LookupRateLimiter.MAX_FAILURES; i++) {
            assertThat(lookup("ZZZZZZZZ", "012 345 678").getStatus()).isEqualTo(404);
        }

        MockHttpServletResponse blocked = lookup(booking.getReference(), "012 345 678");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(code(blocked)).isEqualTo("TOO_MANY_ATTEMPTS");
    }

    @Test
    void hitsDoNotCountTowardsTheLimit() throws Exception {
        for (int i = 0; i < LookupRateLimiter.MAX_FAILURES + 2; i++) {
            assertThat(lookup(booking.getReference(), "012345678").getStatus()).isEqualTo(200);
        }
    }
}
