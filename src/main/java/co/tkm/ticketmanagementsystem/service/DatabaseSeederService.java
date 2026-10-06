package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.*;
import co.tkm.ticketmanagementsystem.model.*;
import co.tkm.ticketmanagementsystem.repository.*;
import co.tkm.ticketmanagementsystem.service.CambodiaSeedCatalog.CinemaSeed;
import co.tkm.ticketmanagementsystem.service.CambodiaSeedCatalog.FoodSeed;
import co.tkm.ticketmanagementsystem.service.CambodiaSeedCatalog.HallPlan;
import co.tkm.ticketmanagementsystem.service.CambodiaSeedCatalog.MovieSeed;
import co.tkm.ticketmanagementsystem.service.CambodiaSeedCatalog.PersonSeed;
import co.tkm.ticketmanagementsystem.service.CambodiaSeedCatalog.PromoSeed;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DatabaseSeederService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeederService.class);
    private static final String MOVIE_URL_PREFIX = "/uploads/movies/";
    private static final String FOOD_URL_PREFIX = "/uploads/food-items/";
    private static final LocalTime[] SHOW_TIMES = {
            LocalTime.of(10, 30),
            LocalTime.of(13, 15),
            LocalTime.of(16, 0),
            LocalTime.of(18, 45),
            LocalTime.of(21, 15)
    };

    private final UserRepository userRepository;
    private final CinemaRepository cinemaRepository;
    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final FoodItemRepository foodItemRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final BookingRepository bookingRepository;
    private final BookingFoodItemRepository bookingFoodItemRepository;
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final MovieReminderRepository movieReminderRepository;

    @Value("${app.seed.counts.users:24}")
    private int userCount;

    @Value("${app.seed.counts.cinemas:6}")
    private int cinemaCount;

    @Value("${app.seed.counts.hallsPerCinema:3}")
    private int hallsPerCinema;

    @Value("${app.seed.counts.movies:15}")
    private int movieCount;

    @Value("${app.seed.counts.showtimesPerMovie:5}")
    private int showtimesPerMovie;

    @Value("${app.seed.counts.foodItems:10}")
    private int foodItemCount;

    @Value("${app.seed.counts.promoCodes:5}")
    private int promoCodeCount;

    @Value("${app.seed.counts.bookings:20}")
    private int bookingCount;

    @Value("${app.uploads.movies-dir:uploads/movies}")
    private String uploadsMoviesDir;

    @Value("${app.uploads.food-items-dir:uploads/food-items}")
    private String uploadsFoodItemsDir;

    @Transactional
    public void seed() {
        log.info("Starting Cambodia catalog seeding...");
        long start = System.currentTimeMillis();

        List<User> users = seedUsers();
        List<Cinema> cinemas = seedCinemas();
        List<Hall> halls = seedHalls(cinemas);
        List<Seat> seats = seedSeats(halls);
        List<FoodItem> foodItems = seedFoodItems();
        List<PromoCode> promoCodes = seedPromoCodes();
        List<Movie> movies = seedMovies();
        List<Showtime> showtimes = seedShowtimes(movies, halls);
        List<ShowtimeSeat> showtimeSeats = seedShowtimeSeats(showtimes, seats);
        List<Booking> bookings = seedBookings(users, showtimes, showtimeSeats, foodItems);

        long end = System.currentTimeMillis();
        log.info("Database seeding completed in {} ms.", (end - start));
        printReport(users, cinemas, halls, seats, movies, showtimes, showtimeSeats, foodItems, promoCodes, bookings);
    }

    @Transactional
    public void rollback() {
        log.info("Rolling back all seeded data...");
        ticketRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        bookingFoodItemRepository.deleteAllInBatch();
        bookingRepository.deleteAllInBatch();
        showtimeSeatRepository.deleteAllInBatch();
        showtimeRepository.deleteAllInBatch();
        movieReminderRepository.deleteAllInBatch();
        movieRepository.deleteAllInBatch();
        promoCodeRepository.deleteAllInBatch();
        foodItemRepository.deleteAllInBatch();
        seatRepository.deleteAllInBatch();
        hallRepository.deleteAllInBatch();
        cinemaRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        log.info("Rollback completed.");
    }

    private List<User> seedUsers() {
        List<User> users = new ArrayList<>();
        for (PersonSeed person : take(CambodiaSeedCatalog.CUSTOMERS, userCount)) {
            User user = new User();
            user.setName(person.name());
            user.setEmail(person.email());
            user.setPhone(person.phone());
            user.setPassword(User.KEYCLOAK_MANAGED_PASSWORD);
            user.setRole(Role.CUSTOMER);
            user.setStatus(UserStatus.ACTIVE);
            user.setPoints(person.points());
            users.add(user);
        }
        return userRepository.saveAll(users);
    }

    private List<Cinema> seedCinemas() {
        List<Cinema> cinemas = new ArrayList<>();
        for (CinemaSeed source : take(CambodiaSeedCatalog.CINEMAS, cinemaCount)) {
            Cinema cinema = new Cinema();
            cinema.setName(source.name());
            cinema.setAddress(source.address());
            cinema.setTotalHalls(Math.min(hallsPerCinema, CambodiaSeedCatalog.HALL_PLANS.size()));
            cinema.setOpeningHours(source.openingHours());
            cinema.setLatitude(source.latitude());
            cinema.setLongitude(source.longitude());
            cinemas.add(cinema);
        }
        return cinemaRepository.saveAll(cinemas);
    }

    private List<Hall> seedHalls(List<Cinema> cinemas) {
        List<Hall> halls = new ArrayList<>();
        List<CinemaSeed> sources = take(CambodiaSeedCatalog.CINEMAS, cinemas.size());
        int hallLimit = Math.min(hallsPerCinema, CambodiaSeedCatalog.HALL_PLANS.size());
        for (int i = 0; i < cinemas.size(); i++) {
            Cinema cinema = cinemas.get(i);
            CinemaSeed source = sources.get(i);
            for (int h = 0; h < hallLimit; h++) {
                HallPlan plan = CambodiaSeedCatalog.HALL_PLANS.get(h);
                Hall hall = new Hall();
                hall.setName(source.shortName() + " · " + plan.suffix());
                hall.setCinema(cinema);
                hall.setTotalSeats(60);
                hall.setCapacity(60);
                hall.setCity(source.city());
                hall.setAddress(cinema.getAddress());
                hall.setAmenities(plan.amenities());
                hall.setType(plan.type());
                hall.setHasDolbyAtmos(plan.dolbyAtmos());
                halls.add(hall);
            }
        }
        return hallRepository.saveAll(halls);
    }

    private List<Seat> seedSeats(List<Hall> halls) {
        List<Seat> seats = new ArrayList<>();
        for (Hall hall : halls) {
            for (int r = 0; r < 6; r++) {
                char rowLetter = (char) ('A' + r);
                SeatType type = (r == 0) ? SeatType.COUPLE : SeatType.NORMAL;
                for (int c = 1; c <= 10; c++) {
                    Seat seat = new Seat();
                    seat.setHall(hall);
                    seat.setRowNumber(r + 1);
                    seat.setSeatCode(rowLetter + String.valueOf(c));
                    seat.setSeatType(type);
                    seats.add(seat);
                }
            }
        }
        return seatRepository.saveAll(seats);
    }

    private List<FoodItem> seedFoodItems() {
        List<FoodItem> items = new ArrayList<>();
        for (FoodSeed source : take(CambodiaSeedCatalog.FOOD, foodItemCount)) {
            FoodItem item = new FoodItem();
            item.setName(source.name());
            item.setDescription(source.description());
            item.setPrice(source.price());
            item.setImageUrl(publishImage("seed/food/" + source.slug() + ".jpg",
                    uploadsFoodItemsDir, FOOD_URL_PREFIX, source.slug() + ".jpg"));
            item.setAvailable(true);
            item.setStockQuantity(0);
            items.add(item);
        }
        return foodItemRepository.saveAll(items);
    }

    private List<PromoCode> seedPromoCodes() {
        List<PromoCode> promos = new ArrayList<>();
        for (PromoSeed source : take(CambodiaSeedCatalog.PROMOS, promoCodeCount)) {
            PromoCode promo = new PromoCode();
            promo.setCode(source.code());
            promo.setDiscountType(DiscountType.PERCENT);
            promo.setDiscountPercent(source.discountPercent());
            promo.setMinPurchase(source.minPurchase());
            promo.setValidUntil(LocalDate.now().plusDays(90));
            promo.setMaxUses(source.maxUses());
            promo.setUsedCount(0);
            promo.setActive(true);
            promos.add(promo);
        }
        return promoCodeRepository.saveAll(promos);
    }

    private List<Movie> seedMovies() {
        List<Movie> movies = new ArrayList<>();
        for (MovieSeed source : take(CambodiaSeedCatalog.MOVIES, movieCount)) {
            Movie movie = new Movie();
            movie.setTitle(source.title());
            movie.setDescription(source.description());
            movie.setGenre(source.genre());
            movie.setDirector(source.director());
            movie.setCast(source.cast());
            movie.setLanguage(source.language());
            movie.setFormat("2D");
            movie.setRating(source.rating());
            movie.setDurationMinutes(source.durationMinutes());
            movie.setReleaseDate(source.releaseDate());
            movie.setStatus(source.status());
            movie.setImagePath(publishImage("seed/movies/" + source.slug() + "-poster.jpg",
                    uploadsMoviesDir, MOVIE_URL_PREFIX, source.slug() + "-poster.jpg"));
            movie.setBannerPath(publishImage("seed/movies/" + source.slug() + "-banner.jpg",
                    uploadsMoviesDir, MOVIE_URL_PREFIX, source.slug() + "-banner.jpg"));
            movies.add(movie);
        }
        return movieRepository.saveAll(movies);
    }

    private List<Showtime> seedShowtimes(List<Movie> movies, List<Hall> halls) {
        List<Showtime> showtimes = new ArrayList<>();
        if (halls.isEmpty()) {
            return showtimes;
        }
        Set<String> usedSlots = new HashSet<>();
        int cursor = 0;
        for (Movie movie : movies) {
            if (movie.getStatus() != MovieStatus.NOW_SHOWING) {
                continue;
            }
            int placed = 0;
            int attempts = 0;
            while (placed < showtimesPerMovie && attempts < halls.size() * 14) {
                attempts++;
                Hall hall = halls.get(cursor % halls.size());
                LocalDate date = LocalDate.now().plusDays(cursor % 7L);
                LocalTime time = SHOW_TIMES[cursor % SHOW_TIMES.length];
                cursor++;
                String key = hall.getId() + "|" + date + "|" + time;
                if (!usedSlots.add(key)) {
                    continue;
                }
                boolean atmos = "ATMOS".equals(hall.getType());
                Showtime st = new Showtime();
                st.setMovie(movie);
                st.setHall(hall);
                st.setShowDate(date);
                st.setShowTime(time);
                st.setNormalPrice(atmos ? CambodiaSeedCatalog.ATMOS_SEAT : CambodiaSeedCatalog.STANDARD_SEAT);
                st.setCouplePrice(atmos ? CambodiaSeedCatalog.ATMOS_COUPLE : CambodiaSeedCatalog.STANDARD_COUPLE);
                st.setBlocked(false);
                showtimes.add(st);
                placed++;
            }
        }
        return showtimeRepository.saveAll(showtimes);
    }

    private List<ShowtimeSeat> seedShowtimeSeats(List<Showtime> showtimes, List<Seat> allSeats) {
        List<ShowtimeSeat> showtimeSeats = new ArrayList<>();
        Map<Long, List<Seat>> seatsByHall = new HashMap<>();
        for (Seat s : allSeats) {
            seatsByHall.computeIfAbsent(s.getHall().getId(), k -> new ArrayList<>()).add(s);
        }

        int batchSize = 1000;
        for (Showtime st : showtimes) {
            List<Seat> hallSeats = seatsByHall.get(st.getHall().getId());
            if (hallSeats == null) {
                continue;
            }
            for (Seat seat : hallSeats) {
                ShowtimeSeat ss = new ShowtimeSeat();
                ss.setShowtime(st);
                ss.setSeat(seat);
                ss.setStatus(ShowtimeSeatStatus.AVAILABLE);
                showtimeSeats.add(ss);

                if (showtimeSeats.size() >= batchSize) {
                    showtimeSeatRepository.saveAll(showtimeSeats);
                    showtimeSeatRepository.flush();
                    showtimeSeats.clear();
                }
            }
        }
        if (!showtimeSeats.isEmpty()) {
            showtimeSeatRepository.saveAll(showtimeSeats);
        }
        return showtimeSeatRepository.findAll();
    }

    private List<Booking> seedBookings(List<User> users, List<Showtime> showtimes,
                                       List<ShowtimeSeat> allShowtimeSeats, List<FoodItem> foodItems) {
        List<Booking> bookings = new ArrayList<>();
        List<Payment> payments = new ArrayList<>();
        List<Ticket> tickets = new ArrayList<>();
        List<BookingFoodItem> bfItems = new ArrayList<>();
        if (showtimes.isEmpty() || users.isEmpty()) {
            return bookings;
        }

        Map<Long, List<ShowtimeSeat>> ssByShowtime = new HashMap<>();
        for (ShowtimeSeat ss : allShowtimeSeats) {
            ssByShowtime.computeIfAbsent(ss.getShowtime().getId(), k -> new ArrayList<>()).add(ss);
        }

        for (int i = 0; i < bookingCount; i++) {
            Showtime st = showtimes.get(i % showtimes.size());
            User user = users.get(i % users.size());
            List<ShowtimeSeat> availableSeats = ssByShowtime.getOrDefault(st.getId(), List.of()).stream()
                    .filter(s -> s.getStatus() == ShowtimeSeatStatus.AVAILABLE)
                    .filter(s -> s.getSeat().getSeatType() == SeatType.NORMAL)
                    .toList();
            if (availableSeats.isEmpty()) {
                continue;
            }

            ShowtimeSeat bookedSeat = availableSeats.get(i % availableSeats.size());
            bookedSeat.setStatus(ShowtimeSeatStatus.BOOKED);
            showtimeSeatRepository.save(bookedSeat);

            BigDecimal total = st.getNormalPrice();
            Booking booking = new Booking();
            booking.setUser(user);
            booking.setShowtime(st);
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setBookedAt(LocalDateTime.now().minusDays(i % 5L).minusHours(i % 8L));
            booking.setPaymentMethod(CambodiaSeedCatalog.PAYMENT_METHODS[i % CambodiaSeedCatalog.PAYMENT_METHODS.length]);

            if (!foodItems.isEmpty() && i % 2 == 0) {
                FoodItem food = foodItems.get(i % foodItems.size());
                int quantity = (i % 2) + 1;
                BookingFoodItem bfi = new BookingFoodItem();
                bfi.setBooking(booking);
                bfi.setFoodItem(food);
                bfi.setQuantity(quantity);
                bfi.setUnitPrice(food.getPrice());
                bfItems.add(bfi);
                total = total.add(food.getPrice().multiply(BigDecimal.valueOf(quantity)));
            }
            booking.setTotalAmount(total);
            booking = bookingRepository.save(booking);
            bookings.add(booking);

            Payment payment = new Payment();
            payment.setBooking(booking);
            payment.setAmount(booking.getTotalAmount());
            payment.setMethod(booking.getPaymentMethod());
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(booking.getBookedAt());
            payments.add(payment);

            Ticket ticket = new Ticket();
            ticket.setBooking(booking);
            ticket.setSeat(bookedSeat.getSeat());
            ticket.setTicketCode("TKM-" + String.format("%05d", i + 1));
            ticket.setStatus(TicketStatus.ACTIVE);
            ticket.setIssuedAt(booking.getBookedAt());
            tickets.add(ticket);
        }

        paymentRepository.saveAll(payments);
        ticketRepository.saveAll(tickets);
        bookingFoodItemRepository.saveAll(bfItems);
        return bookings;
    }

    private String publishImage(String classpath, String uploadDir, String urlPrefix, String filename) {
        ClassPathResource resource = new ClassPathResource(classpath);
        if (!resource.exists()) {
            log.warn("Seed image missing: {}", classpath);
            return null;
        }
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path destination = dir.resolve(filename);
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return urlPrefix + filename;
        } catch (IOException ex) {
            log.warn("Could not copy seed image {}: {}", classpath, ex.getMessage());
            return null;
        }
    }

    private static <T> List<T> take(List<T> source, int count) {
        if (count <= 0 || source.isEmpty()) {
            return List.of();
        }
        return source.subList(0, Math.min(count, source.size()));
    }

    private void printReport(List<User> users, List<Cinema> cinemas, List<Hall> halls, List<Seat> seats,
                             List<Movie> movies, List<Showtime> showtimes, List<ShowtimeSeat> showtimeSeats,
                             List<FoodItem> foodItems, List<PromoCode> promoCodes, List<Booking> bookings) {
        log.info("========================================");
        log.info("       DATABASE SEEDING REPORT          ");
        log.info("========================================");
        log.info("Users Created:         {}", users.size());
        log.info("Cinemas Created:       {}", cinemas.size());
        log.info("Halls Created:         {}", halls.size());
        log.info("Seats Created:         {}", seats.size());
        log.info("Food Items Created:    {}", foodItems.size());
        log.info("Promo Codes Created:   {}", promoCodes.size());
        log.info("Movies Created:        {}", movies.size());
        log.info("Showtimes Created:     {}", showtimes.size());
        log.info("Showtime Seats Created:{}", showtimeSeats.size());
        log.info("Bookings Created:      {}", bookings.size());
        log.info("========================================");
    }
}
