package co.tkm.ticketmanagementsystem.config;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.enums.Role;
import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.model.FoodItem;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.PromoCode;
import co.tkm.ticketmanagementsystem.model.Seat;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.FoodItemRepository;
import co.tkm.ticketmanagementsystem.repository.HallRepository;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import co.tkm.ticketmanagementsystem.repository.PromoCodeRepository;
import co.tkm.ticketmanagementsystem.repository.SeatRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import co.tkm.ticketmanagementsystem.service.ShowtimeService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final FoodItemRepository foodItemRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ShowtimeService showtimeService;

    @Value("${app.seed.force:false}")
    private boolean force;

    @Value("${app.seed.halls:3}")
    private int hallCount;

    @Value("${app.seed.days:14}")
    private int dayCount;

    @Value("${app.seed.showtimesPerHallPerDay:3}")
    private int showtimesPerHallPerDay;

    @Value("${app.seed.customers:25}")
    private int customerCount;

    @Value("${app.seed.randomSeed:20260503}")
    private long randomSeed;

    @Override
    @Transactional
    public void run(String... args) {
        if (!force && isAlreadySeeded()) {
            log.info("Seed skipped (data already exists). Set app.seed.force=true to add more demo data.");
            return;
        }

        long start = System.currentTimeMillis();
        Random rnd = new Random(randomSeed);

        List<Hall> halls = seedHalls(rnd, hallCount);
        seedSeatsForHalls(halls);

        List<Movie> nowShowing = seedMovies(rnd, 8, MovieStatus.NOW_SHOWING);
        seedMovies(rnd, 6, MovieStatus.COMING_SOON);

        List<Showtime> showtimes = seedShowtimes(rnd, halls, nowShowing, dayCount, showtimesPerHallPerDay);
        seedShowtimeSeatsInBulk(showtimes);

        seedFoodItems();
        seedPromoCodes();
        seedCustomers(rnd, customerCount);

        validateIntegrity(halls, showtimes);

        long tookMs = System.currentTimeMillis() - start;
        log.info("Seed completed: halls={}, showtimes={}, customers={} ({} ms)",
                halls.size(), showtimes.size(), customerCount, tookMs);
    }

    private boolean isAlreadySeeded() {
        return hallRepository.count() > 0 || movieRepository.count() > 0 || showtimeRepository.count() > 0;
    }

    private List<Hall> seedHalls(Random rnd, int count) {
        List<String> cities = List.of("Phnom Penh", "Siem Reap", "Battambang", "Sihanoukville");
        List<Hall> halls = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            Hall hall = new Hall();
            hall.setName(("Cinema " + i).toUpperCase(Locale.ROOT));
            hall.setTotalSeats(60);
            hall.setCity(cities.get(rnd.nextInt(cities.size())));
            hall.setAddress("Street " + (10 + rnd.nextInt(80)) + ", Block " + (1 + rnd.nextInt(12)));
            hall.setAmenities("Dolby Atmos, Recliners, Snacks, Parking");
            halls.add(hall);
        }

        List<Hall> saved = hallRepository.saveAll(halls);
        log.info("Seeded halls: {}", saved.size());
        return saved;
    }

    private void seedSeatsForHalls(List<Hall> halls) {
        for (Hall hall : halls) {
            if (!force && !seatRepository.findByHall(hall).isEmpty()) {
                continue;
            }
            List<Seat> seats = generateHallSeats(hall);
            seatRepository.saveAll(seats);
            log.info("Seeded seats for hall {}: {}", hall.getName(), seats.size());
        }
    }

    private List<Seat> generateHallSeats(Hall hall) {
        int rows = 6;
        int cols = 10;
        List<Seat> seats = new ArrayList<>(rows * cols);

        for (int r = 0; r < rows; r++) {
            char rowLetter = (char) ('A' + r);
            SeatType type = (r == 0) ? SeatType.COUPLE : SeatType.NORMAL;
            int rowNumber = r + 1;

            for (int c = 1; c <= cols; c++) {
                Seat seat = new Seat();
                seat.setHall(hall);
                seat.setRowNumber(rowNumber);
                seat.setSeatCode(rowLetter + String.valueOf(c));
                seat.setSeatType(type);
                seats.add(seat);
            }
        }

        return seats;
    }

    private List<Movie> seedMovies(Random rnd, int count, MovieStatus status) {
        List<String> genres = List.of("Action", "Drama", "Comedy", "Sci-Fi", "Horror", "Animation");
        List<String> languages = List.of("English", "Khmer", "Thai", "Korean", "Japanese");
        List<String> formats = List.of("2D", "3D", "IMAX");
        List<String> ratings = List.of("G", "PG", "PG-13", "R");

        List<Movie> movies = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Movie m = new Movie();
            m.setTitle(status == MovieStatus.COMING_SOON
                    ? "Upcoming " + (i + 1) + " — " + pick(genres, rnd)
                    : "Now Showing " + (i + 1) + " — " + pick(genres, rnd));
            m.setDescription("A cinematic story with twists, emotion, and big-screen moments.");
            m.setGenre(pick(genres, rnd));
            m.setDirector("Director " + (char) ('A' + rnd.nextInt(12)));
            m.setLanguage(pick(languages, rnd));
            m.setFormat(pick(formats, rnd));
            m.setCast("Actor " + (char) ('A' + rnd.nextInt(20)) + ", Actor " + (char) ('A' + rnd.nextInt(20)));
            m.setTrailerUrl("https://www.youtube.com/embed/dQw4w9WgXcQ");
            m.setRating(pick(ratings, rnd));
            if (status == MovieStatus.COMING_SOON) {
                m.setReleaseDate(LocalDate.now().plusDays(7L + rnd.nextInt(45)));
            } else {
                m.setReleaseDate(LocalDate.now().minusDays(rnd.nextInt(45)));
            }
            m.setDurationMinutes(95 + rnd.nextInt(46));
            m.setStatus(status);
            movies.add(m);
        }

        List<Movie> saved = movieRepository.saveAll(movies);
        log.info("Seeded movies ({}): {}", status, saved.size());
        return saved;
    }

    private List<Showtime> seedShowtimes(Random rnd, List<Hall> halls, List<Movie> movies,
                                         int days, int perHallPerDay) {
        LocalDate startDate = LocalDate.now();
        List<Showtime> created = new ArrayList<>();

        for (Hall hall : halls) {
            for (int d = 0; d < days; d++) {
                LocalDate date = startDate.plusDays(d);
                LocalTime time = LocalTime.of(10, 0);

                for (int s = 0; s < perHallPerDay; s++) {
                    Movie movie = movies.get(rnd.nextInt(movies.size()));
                    int duration = movie.getDurationMinutes();

                    Showtime showtime = new Showtime();
                    showtime.setHall(hall);
                    showtime.setMovie(movie);
                    showtime.setShowDate(date);
                    showtime.setShowTime(time);
                    showtime.setNormalPrice(new BigDecimal("5.00").add(new BigDecimal(rnd.nextInt(4))));
                    showtime.setCouplePrice(new BigDecimal("8.00").add(new BigDecimal(rnd.nextInt(5))));
                    showtime.setBlocked(false);

                    showtimeService.validateNoOverlap(hall.getId(), date, time, duration, null);
                    created.add(showtime);

                    time = time.plusMinutes(duration + 20L);
                }
            }
        }

        List<Showtime> saved = showtimeRepository.saveAll(created);
        log.info("Seeded showtimes: {}", saved.size());
        return saved;
    }

    private void seedShowtimeSeatsInBulk(List<Showtime> showtimes) {
        if (showtimes.isEmpty()) {
            return;
        }

        int batchSize = 500;
        List<ShowtimeSeat> buffer = new ArrayList<>(batchSize);

        for (Showtime showtime : showtimes) {
            if (!force && !showtimeSeatRepository.findByShowtime(showtime).isEmpty()) {
                continue;
            }

            List<Seat> seats = seatRepository.findByHall(showtime.getHall());
            for (Seat seat : seats) {
                ShowtimeSeat ss = new ShowtimeSeat();
                ss.setShowtime(showtime);
                ss.setSeat(seat);
                ss.setStatus(ShowtimeSeatStatus.AVAILABLE);
                buffer.add(ss);

                if (buffer.size() >= batchSize) {
                    showtimeSeatRepository.saveAll(buffer);
                    showtimeSeatRepository.flush();
                    buffer.clear();
                }
            }
        }

        if (!buffer.isEmpty()) {
            showtimeSeatRepository.saveAll(buffer);
            showtimeSeatRepository.flush();
        }
        log.info("Seeded showtime seats (bulk)");
    }

    private void seedFoodItems() {
        if (!force && foodItemRepository.count() > 0) {
            return;
        }

        List<FoodItem> items = List.of(
                new FoodItem(null, "Popcorn (Small)", "Freshly popped", new BigDecimal("2.50"), null, true, 0),
                new FoodItem(null, "Popcorn (Large)", "Extra butter available", new BigDecimal("4.00"), null, true, 0),
                new FoodItem(null, "Soda", "Chilled soft drink", new BigDecimal("1.75"), null, true, 0),
                new FoodItem(null, "Nachos", "Cheese dip included", new BigDecimal("3.50"), null, true, 0)
        );
        foodItemRepository.saveAll(items);
        log.info("Seeded food items: {}", items.size());
    }

    private void seedPromoCodes() {
        if (!force && promoCodeRepository.count() > 0) {
            return;
        }

        List<PromoCode> codes = new ArrayList<>();
        PromoCode welcome = new PromoCode();
        welcome.setCode("WELCOME10");
        welcome.setDiscountPercent(10);
        welcome.setMinPurchase(new BigDecimal("10.00"));
        welcome.setValidUntil(LocalDate.now().plusMonths(3));
        welcome.setActive(true);
        codes.add(welcome);

        PromoCode weekend = new PromoCode();
        weekend.setCode("WEEKEND5");
        weekend.setDiscountPercent(5);
        weekend.setMinPurchase(new BigDecimal("5.00"));
        weekend.setValidUntil(LocalDate.now().plusMonths(1));
        weekend.setActive(true);
        codes.add(weekend);

        promoCodeRepository.saveAll(codes);
        log.info("Seeded promo codes: {}", codes.size());
    }

    private void seedCustomers(Random rnd, int count) {
        for (int i = 1; i <= count; i++) {
            String email = ("user" + i + "@mtms.local").toLowerCase(Locale.ROOT);
            if (!force && userRepository.findByEmail(email).isPresent()) {
                continue;
            }
            User user = new User();
            user.setName("User " + i);
            user.setEmail(email);
            user.setPhone("+855" + (10 + rnd.nextInt(80)) + (100000 + rnd.nextInt(900000)));
            user.setPassword(passwordEncoder.encode("password"));
            user.setRole(Role.CUSTOMER);
            userRepository.save(user);
        }
        log.info("Seeded customers: {}", count);
    }

    private void validateIntegrity(List<Hall> halls, List<Showtime> showtimes) {
        for (Hall hall : halls) {
            int seatCount = seatRepository.findByHall(hall).size();
            if (seatCount != hall.getTotalSeats()) {
                throw new IllegalStateException("Hall seat count mismatch: hall=" + hall.getName() + " seats=" + seatCount);
            }
        }

        if (!showtimes.isEmpty()) {
            Showtime sample = showtimes.get(0);
            int seatCount = seatRepository.findByHall(sample.getHall()).size();
            int showtimeSeatCount = showtimeSeatRepository.findByShowtime(sample).size();
            if (showtimeSeatCount != seatCount) {
                throw new IllegalStateException("ShowtimeSeat mismatch: showtime=" + sample.getId() + " expected=" + seatCount + " actual=" + showtimeSeatCount);
            }
        }
    }

    private static <T> T pick(List<T> list, Random rnd) {
        return list.get(rnd.nextInt(list.size()));
    }
}
