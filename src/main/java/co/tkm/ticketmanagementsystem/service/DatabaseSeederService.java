package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.*;
import co.tkm.ticketmanagementsystem.model.*;
import co.tkm.ticketmanagementsystem.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.javafaker.Faker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DatabaseSeederService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeederService.class);

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
    private final SystemSettingRepository systemSettingRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.counts.users:50}")
    private int userCount;

    @Value("${app.seed.counts.cinemas:3}")
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

    private final Faker faker = new Faker();

    @Transactional
    public void seed() {
        log.info("Starting comprehensive database seeding...");
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
        List<Booking> bookings = seedBookings(users, showtimes, showtimeSeats, foodItems, promoCodes);

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
        String encodedPassword = passwordEncoder.encode("password123");
        for (int i = 0; i < userCount; i++) {
            User user = new User();
            user.setName(faker.name().fullName());
            user.setEmail(UUID.randomUUID().toString().substring(0, 8) + "_" + faker.internet().emailAddress());
            user.setPhone(faker.phoneNumber().cellPhone());
            user.setPassword(encodedPassword);
            user.setRole(Role.CUSTOMER);
            user.setStatus(UserStatus.ACTIVE);
            user.setPoints(faker.number().numberBetween(0, 1000));
            users.add(user);
        }
        return userRepository.saveAll(users);
    }

    private List<Cinema> seedCinemas() {
        List<Cinema> cinemas = new ArrayList<>();
        for (int i = 0; i < cinemaCount; i++) {
            Cinema cinema = new Cinema();
            cinema.setName(faker.company().name() + " Cinema " + i);
            cinema.setAddress(faker.address().fullAddress());
            cinema.setTotalHalls(hallsPerCinema);
            cinema.setOpeningHours("08:00 AM - 11:00 PM");
            cinema.setLatitude(Double.valueOf(faker.address().latitude().replace(",", ".")));
            cinema.setLongitude(Double.valueOf(faker.address().longitude().replace(",", ".")));
            cinemas.add(cinema);
        }
        return cinemaRepository.saveAll(cinemas);
    }

    private List<Hall> seedHalls(List<Cinema> cinemas) {
        List<Hall> halls = new ArrayList<>();
        for (Cinema cinema : cinemas) {
            for (int i = 1; i <= hallsPerCinema; i++) {
                Hall hall = new Hall();
                hall.setName(cinema.getName() + " - Hall " + i);
                hall.setCinema(cinema);
                hall.setTotalSeats(60); // 6 rows * 10 cols
                hall.setCity(faker.address().city());
                hall.setAddress(cinema.getAddress());
                hall.setAmenities("Dolby Atmos, 4K Projection");
                hall.setType(i == 1 ? "IMAX" : "STANDARD");
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
        for (int i = 0; i < foodItemCount; i++) {
            FoodItem item = new FoodItem();
            item.setName(faker.food().dish());
            item.setDescription(faker.lorem().sentence());
            item.setPrice(BigDecimal.valueOf(faker.number().randomDouble(2, 2, 15)));
            item.setAvailable(true);
            items.add(item);
        }
        return foodItemRepository.saveAll(items);
    }

    private List<PromoCode> seedPromoCodes() {
        List<PromoCode> promos = new ArrayList<>();
        for (int i = 0; i < promoCodeCount; i++) {
            PromoCode promo = new PromoCode();
            promo.setCode(faker.regexify("[A-Z0-9]{5}") + i);
            promo.setDiscountType(faker.options().option(DiscountType.class));
            promo.setDiscountPercent(faker.number().numberBetween(5, 20));
            promo.setValidUntil(LocalDate.now().plusDays(30));
            promo.setMaxUses(100);
            promo.setUsedCount(0);
            promo.setActive(true);
            promos.add(promo);
        }
        return promoCodeRepository.saveAll(promos);
    }

    private List<Movie> seedMovies() {
        List<Movie> movies = new ArrayList<>();
        for (int i = 0; i < movieCount; i++) {
            Movie movie = new Movie();
            movie.setTitle(faker.book().title());
            movie.setDescription(faker.lorem().paragraph(3));
            movie.setGenre(faker.book().genre());
            movie.setDirector(faker.name().fullName());
            movie.setCast(faker.name().fullName() + ", " + faker.name().fullName());
            movie.setLanguage(faker.nation().language());
            movie.setFormat("2D, 3D");
            movie.setRating(faker.options().option("G", "PG", "PG-13", "R"));
            movie.setDurationMinutes(faker.number().numberBetween(90, 180));
            movie.setReleaseDate(LocalDate.now().minusDays(faker.number().numberBetween(0, 30)));
            movie.setStatus(faker.options().option(MovieStatus.NOW_SHOWING, MovieStatus.COMING_SOON));
            movies.add(movie);
        }
        return movieRepository.saveAll(movies);
    }

    private List<Showtime> seedShowtimes(List<Movie> movies, List<Hall> halls) {
        List<Showtime> showtimes = new ArrayList<>();
        for (Movie movie : movies) {
            if (movie.getStatus() != MovieStatus.NOW_SHOWING) continue;
            for (int i = 0; i < showtimesPerMovie; i++) {
                Showtime st = new Showtime();
                st.setMovie(movie);
                st.setHall(halls.get(faker.number().numberBetween(0, halls.size())));
                st.setShowDate(LocalDate.now().plusDays(faker.number().numberBetween(0, 7)));
                st.setShowTime(LocalTime.of(faker.number().numberBetween(10, 22), 0));
                st.setNormalPrice(BigDecimal.valueOf(faker.number().randomDouble(2, 5, 10)));
                st.setCouplePrice(BigDecimal.valueOf(faker.number().randomDouble(2, 12, 20)));
                showtimes.add(st);
            }
        }
        return showtimeRepository.saveAll(showtimes);
    }

    private List<ShowtimeSeat> seedShowtimeSeats(List<Showtime> showtimes, List<Seat> allSeats) {
        List<ShowtimeSeat> showtimeSeats = new ArrayList<>();
        // Group seats by hall
        Map<Long, List<Seat>> seatsByHall = new HashMap<>();
        for (Seat s : allSeats) {
            seatsByHall.computeIfAbsent(s.getHall().getId(), k -> new ArrayList<>()).add(s);
        }

        int batchSize = 1000;
        for (Showtime st : showtimes) {
            List<Seat> hallSeats = seatsByHall.get(st.getHall().getId());
            if (hallSeats != null) {
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
        }
        if (!showtimeSeats.isEmpty()) {
            showtimeSeatRepository.saveAll(showtimeSeats);
        }
        return showtimeSeatRepository.findAll();
    }

    private List<Booking> seedBookings(List<User> users, List<Showtime> showtimes, List<ShowtimeSeat> allShowtimeSeats, List<FoodItem> foodItems, List<PromoCode> promoCodes) {
        List<Booking> bookings = new ArrayList<>();
        List<Payment> payments = new ArrayList<>();
        List<Ticket> tickets = new ArrayList<>();
        List<BookingFoodItem> bfItems = new ArrayList<>();

        Map<Long, List<ShowtimeSeat>> ssByShowtime = new HashMap<>();
        for (ShowtimeSeat ss : allShowtimeSeats) {
            ssByShowtime.computeIfAbsent(ss.getShowtime().getId(), k -> new ArrayList<>()).add(ss);
        }

        for (int i = 0; i < bookingCount; i++) {
            if (showtimes.isEmpty() || users.isEmpty()) break;
            
            Showtime st = showtimes.get(faker.number().numberBetween(0, showtimes.size()));
            User user = users.get(faker.number().numberBetween(0, users.size()));
            
            List<ShowtimeSeat> availableSeats = ssByShowtime.get(st.getId()).stream()
                    .filter(s -> s.getStatus() == ShowtimeSeatStatus.AVAILABLE)
                    .toList();
                    
            if (availableSeats.isEmpty()) continue;
            
            ShowtimeSeat bookedSeat = availableSeats.get(faker.number().numberBetween(0, availableSeats.size()));
            bookedSeat.setStatus(ShowtimeSeatStatus.BOOKED);
            showtimeSeatRepository.save(bookedSeat);

            Booking booking = new Booking();
            booking.setUser(user);
            booking.setShowtime(st);
            booking.setTotalAmount(st.getNormalPrice());
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setBookedAt(LocalDateTime.now());
            booking.setPaymentMethod("CREDIT_CARD");
            booking = bookingRepository.save(booking);
            bookings.add(booking);

            Payment payment = new Payment();
            payment.setBooking(booking);
            payment.setAmount(booking.getTotalAmount());
            payment.setMethod("CREDIT_CARD");
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
            payments.add(payment);

            Ticket ticket = new Ticket();
            ticket.setBooking(booking);
            ticket.setSeat(bookedSeat.getSeat());
            ticket.setTicketCode(UUID.randomUUID().toString());
            ticket.setStatus(TicketStatus.ACTIVE);
            ticket.setIssuedAt(LocalDateTime.now());
            tickets.add(ticket);
            
            if (faker.bool().bool()) {
                FoodItem food = foodItems.get(faker.number().numberBetween(0, foodItems.size()));
                BookingFoodItem bfi = new BookingFoodItem();
                bfi.setBooking(booking);
                bfi.setFoodItem(food);
                bfi.setQuantity(faker.number().numberBetween(1, 3));
                bfi.setUnitPrice(food.getPrice());
                bfItems.add(bfi);
            }
        }
        
        paymentRepository.saveAll(payments);
        ticketRepository.saveAll(tickets);
        bookingFoodItemRepository.saveAll(bfItems);
        
        return bookings;
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