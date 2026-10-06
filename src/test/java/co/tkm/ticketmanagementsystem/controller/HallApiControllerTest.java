package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Cinema;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.CinemaRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sprint 10 hall reads against H2, called anonymously the way a guest would. */
@SpringBootTest
@AutoConfigureMockMvc
class HallApiControllerTest {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Phnom_Penh");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private HallRepository hallRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private ShowtimeRepository showtimeRepository;

    private String suffix;
    private Hall hall;
    private Movie movie;
    private LocalDate today;

    @BeforeEach
    void seed() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        today = LocalDate.now(APP_ZONE);

        Cinema zeta = cinema("Zeta " + suffix, "10:00 - 22:00");
        Cinema alpha = cinema("Alpha " + suffix, "09:00 - 23:00");
        hall(zeta, "A hall " + suffix);
        hall = hall(alpha, "B hall " + suffix);
        hall(alpha, "A hall Atmos " + suffix);

        movie = new Movie();
        movie.setTitle("Hall IT " + suffix);
        movie.setGenre("Drama");
        movie.setDurationMinutes(120);
        movie.setStatus(MovieStatus.NOW_SHOWING);
        movie.setReleaseDate(today);
        movie.setImagePath("/uploads/movies/poster.jpg");
        movie = movieRepository.save(movie);
    }

    private Cinema cinema(String name, String hours) {
        Cinema c = new Cinema();
        c.setName(name);
        c.setOpeningHours(hours);
        c.setLatitude(11.5);
        c.setLongitude(104.9);
        return cinemaRepository.save(c);
    }

    private Hall hall(Cinema cinema, String name) {
        Hall h = new Hall();
        h.setName(name);
        h.setCinema(cinema);
        h.setCity("Phnom Penh");
        h.setType("ATMOS");
        h.setTotalSeats(60);
        h.setHasDolbyAtmos(true);
        h.setAmenities("Dolby Atmos, 4K");
        return hallRepository.save(h);
    }

    private Showtime show(LocalDate date, LocalTime time, boolean blocked) {
        Showtime s = new Showtime();
        s.setMovie(movie);
        s.setHall(hall);
        s.setShowDate(date);
        s.setShowTime(time);
        s.setNormalPrice(new BigDecimal("5.00"));
        s.setCouplePrice(new BigDecimal("8.00"));
        s.setBlocked(blocked);
        return showtimeRepository.save(s);
    }

    private JsonNode getJson(String url) throws Exception {
        String body = mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void listHasEveryHallWithItsCinemaOrderedByCinemaThenHall() throws Exception {
        JsonNode rows = getJson("/api/halls");

        List<String> mine = new ArrayList<>();
        JsonNode bHall = null;
        for (JsonNode row : rows) {
            if (row.path("name").asText().endsWith(suffix)) {
                mine.add(row.path("cinemaName").asText() + " / " + row.path("name").asText());
                if (row.path("id").asLong() == hall.getId()) bHall = row;
            }
        }
        assertThat(mine).containsExactly(
                "Alpha " + suffix + " / A hall Atmos " + suffix,
                "Alpha " + suffix + " / B hall " + suffix,
                "Zeta " + suffix + " / A hall " + suffix);
        assertThat(bHall).isNotNull();
        assertThat(bHall.path("openingHours").asText()).isEqualTo("09:00 - 23:00");
        assertThat(bHall.path("cinemaId").asText()).isEqualTo(hall.getCinema().getId().toString());
        assertThat(bHall.path("hasDolbyAtmos").asBoolean()).isTrue();
        assertThat(bHall.path("totalSeats").asInt()).isEqualTo(60);
        assertThat(bHall.has("latitude")).isFalse();
        assertThat(bHall.has("longitude")).isFalse();
    }

    @Test
    void scheduleDropsBlockedAndStartedShowsAndOrdersByTime() throws Exception {
        LocalDate tomorrow = today.plusDays(1);
        Showtime late = show(tomorrow, LocalTime.of(21, 0), false);
        Showtime early = show(tomorrow, LocalTime.of(10, 0), false);
        show(tomorrow, LocalTime.of(15, 0), true);           // blocked
        show(today.minusDays(1), LocalTime.of(20, 0), false); // already started

        JsonNode body = getJson("/api/halls/" + hall.getId() + "/showtimes?date=" + tomorrow);

        assertThat(body.path("selectedDate").asText()).isEqualTo(tomorrow.toString());
        assertThat(body.path("availableDates")).extracting(JsonNode::asText).containsExactly(tomorrow.toString());
        assertThat(body.path("showtimes")).extracting(n -> n.path("id").asLong())
                .containsExactly(early.getId(), late.getId());
        JsonNode first = body.path("showtimes").get(0);
        assertThat(first.path("movieTitle").asText()).isEqualTo(movie.getTitle());
        assertThat(first.path("imagePath").asText()).isEqualTo("/uploads/movies/poster.jpg");
        assertThat(first.path("normalPrice").isNumber()).isTrue();
        assertThat(first.path("couplePrice").decimalValue()).isEqualByComparingTo("8.00");
        assertThat(body.path("hall").path("cinemaName").asText()).isEqualTo("Alpha " + suffix);
    }

    @Test
    void withoutADateTheFirstBookableDateIsSelected() throws Exception {
        show(today.plusDays(3), LocalTime.of(18, 0), false);
        Showtime next = show(today.plusDays(2), LocalTime.of(18, 0), false);
        show(today.plusDays(1), LocalTime.of(18, 0), true); // blocked: its date is not available

        JsonNode body = getJson("/api/halls/" + hall.getId() + "/showtimes");

        assertThat(body.path("selectedDate").asText()).isEqualTo(today.plusDays(2).toString());
        assertThat(body.path("availableDates")).extracting(JsonNode::asText)
                .containsExactly(today.plusDays(2).toString(), today.plusDays(3).toString());
        assertThat(body.path("showtimes")).extracting(n -> n.path("id").asLong()).containsExactly(next.getId());
    }

    @Test
    void aHallWithNothingBookableHasNoSelectedDate() throws Exception {
        JsonNode body = getJson("/api/halls/" + hall.getId() + "/showtimes?date=" + today.plusDays(1));

        assertThat(body.path("selectedDate").isNull()).isTrue();
        assertThat(body.path("availableDates")).isEmpty();
        assertThat(body.path("showtimes")).isEmpty();
    }

    @Test
    void unknownHallIs404WithoutACode() throws Exception {
        String body = mvc.perform(get("/api/halls/999999999/showtimes"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.has("error")).isTrue();
        assertThat(json.path("type").asText()).isEqualTo("NOT_FOUND");
        assertThat(json.has("code")).isFalse();
    }
}
