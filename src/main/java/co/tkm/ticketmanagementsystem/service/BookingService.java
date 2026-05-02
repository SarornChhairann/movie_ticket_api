package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.dto.CheckoutData;
import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.enums.TicketStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import co.tkm.ticketmanagementsystem.repository.TicketRepository;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final PaymentService paymentService;
    private final TicketService ticketService;
    private final EmailService emailService;

    public List<ShowtimeSeat> getSeatsForShowtime(Long showtimeId) {
        Showtime showtime = findShowtime(showtimeId);
        return showtimeSeatRepository.findByShowtimeOrderBySeatRowNumberAscSeatIdAsc(showtime);
    }

    @Transactional(readOnly = true)
    public CheckoutData buildCheckout(Long showtimeId, List<Long> selectedSeatIds, String userEmail) {
        return resolveCheckoutData(showtimeId, selectedSeatIds, userEmail, false);
    }

    @Transactional
    public Booking confirmCheckout(Long showtimeId, List<Long> selectedSeatIds, String userEmail,
                                   String guestName, String guestPhone, String guestEmail) {
        CheckoutData checkoutData = resolveCheckoutData(showtimeId, selectedSeatIds, userEmail, true);

        Booking booking = new Booking();
        booking.setUser(checkoutData.getUser());

        if (checkoutData.getUser() == null) {
            booking.setGuestName(guestName);
            booking.setGuestPhone(guestPhone);
            if (guestEmail != null && !guestEmail.isBlank()) {
                booking.setGuestEmail(guestEmail.trim());
            }
        }

        booking.setShowtime(checkoutData.getShowtime());
        booking.setTotalAmount(checkoutData.getTotalAmount());
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookedAt(LocalDateTime.now());
        Booking savedBooking = bookingRepository.save(booking);

        paymentService.processMockPayment(savedBooking);
        List<Ticket> tickets = ticketService.issueTickets(savedBooking, checkoutData.getSelectedSeats());
        emailService.sendBookingConfirmation(savedBooking, tickets);
        return savedBooking;
    }

    @Transactional
    public void cancelBooking(Long bookingId, String userEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getUser() == null || !booking.getUser().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("You are not authorized to cancel this booking.");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalArgumentException("Booking is already cancelled.");
        }

        // Mark all tickets as cancelled and free up seats
        List<Ticket> tickets = ticketRepository.findByBooking(booking);
        for (Ticket ticket : tickets) {
            ticket.setStatus(TicketStatus.CANCELLED);
            ShowtimeSeat showtimeSeat = showtimeSeatRepository
                    .findByShowtimeAndSeat(booking.getShowtime(), ticket.getSeat())
                    .orElse(null);
            if (showtimeSeat != null) {
                showtimeSeat.setStatus(ShowtimeSeatStatus.AVAILABLE);
                showtimeSeatRepository.save(showtimeSeat);
            }
        }
        ticketRepository.saveAll(tickets);
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        emailService.sendCancellationNotice(booking);
    }

    @Transactional(readOnly = true)
    public Booking findById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + id));
        booking.getShowtime().getMovie().getTitle();
        return booking;
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingsForUser(String userEmail) {
        User user = findUser(userEmail);
        List<Booking> bookings = bookingRepository.findByUser(user);
        bookings.forEach(booking -> {
            booking.getShowtime().getShowDate();
            booking.getShowtime().getShowTime();
            booking.getShowtime().getMovie().getTitle();
        });
        return bookings;
    }

    @Transactional(readOnly = true)
    public List<Booking> getAllBookingsForAdmin() {
        List<Booking> bookings = bookingRepository.findAll();
        bookings.forEach(booking -> {
            booking.getShowtime().getShowDate();
            booking.getShowtime().getShowTime();
            booking.getShowtime().getMovie().getTitle();
            if (booking.getUser() != null) {
                booking.getUser().getName();
            }
        });
        return bookings;
    }

    private CheckoutData resolveCheckoutData(Long showtimeId, List<Long> selectedSeatIds, String userEmail, boolean lockSeats) {
        if (selectedSeatIds == null || selectedSeatIds.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one seat.");
        }

        User user = (userEmail != null) ? userRepository.findByEmail(userEmail).orElse(null) : null;
        Showtime showtime = findShowtime(showtimeId);

        List<ShowtimeSeat> selectedSeats = lockSeats
                ? showtimeSeatRepository.findAllByShowtimeIdAndIdsForUpdate(showtimeId, selectedSeatIds)
                : showtimeSeatRepository.findAllById(selectedSeatIds);

        if (selectedSeats.size() != selectedSeatIds.size()) {
            throw new IllegalArgumentException("Some selected seats are invalid.");
        }

        int normalSeatCount = 0;
        List<SeatCode> coupleSeatCodes = new ArrayList<>();

        for (ShowtimeSeat seat : selectedSeats) {
            if (!seat.getShowtime().getId().equals(showtimeId)) {
                throw new IllegalArgumentException("Selected seats must belong to the chosen showtime.");
            }
            if (seat.getStatus() != ShowtimeSeatStatus.AVAILABLE) {
                throw new IllegalArgumentException("Some seats were already booked. Please reselect seats.");
            }

            if (seat.getSeat().getSeatType() == SeatType.COUPLE) {
                coupleSeatCodes.add(parseSeatCode(seat.getSeat().getSeatCode()));
            } else {
                normalSeatCount++;
            }
        }

        validateCouplePairs(coupleSeatCodes);

        BigDecimal total = BigDecimal.ZERO;
        if (normalSeatCount > 0) {
            total = total.add(showtime.getNormalPrice().multiply(BigDecimal.valueOf(normalSeatCount)));
        }
        if (!coupleSeatCodes.isEmpty()) {
            total = total.add(showtime.getCouplePrice().multiply(BigDecimal.valueOf(coupleSeatCodes.size() / 2)));
        }

        showtime.getMovie().getTitle();
        showtime.getHall().getName();
        selectedSeats.forEach(seat -> seat.getSeat().getSeatCode());
        return new CheckoutData(user, showtime, selectedSeats, total);
    }

    private static final Pattern SEAT_CODE_PATTERN = Pattern.compile("^([A-Za-z]+)(\\d+)$");

    private record SeatCode(String row, int number) {
    }

    private SeatCode parseSeatCode(String seatCode) {
        if (seatCode == null) {
            throw new IllegalArgumentException("Couple seats must be selected in pairs.");
        }
        Matcher matcher = SEAT_CODE_PATTERN.matcher(seatCode.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Couple seats must be selected in pairs.");
        }
        String row = matcher.group(1).toUpperCase();
        int number = Integer.parseInt(matcher.group(2));
        return new SeatCode(row, number);
    }

    private void validateCouplePairs(List<SeatCode> coupleSeats) {
        if (coupleSeats.isEmpty()) {
            return;
        }
        if (coupleSeats.size() % 2 != 0) {
            throw new IllegalArgumentException("Couple seats must be selected in pairs.");
        }

        Map<String, List<Integer>> numbersByRow = new HashMap<>();
        for (SeatCode seat : coupleSeats) {
            numbersByRow.computeIfAbsent(seat.row(), ignored -> new ArrayList<>()).add(seat.number());
        }

        for (List<Integer> rowNumbers : numbersByRow.values()) {
            rowNumbers.sort(Comparator.naturalOrder());
            if (rowNumbers.size() % 2 != 0) {
                throw new IllegalArgumentException("Couple seats must be selected in pairs.");
            }
            for (int i = 0; i < rowNumbers.size(); i += 2) {
                int first = rowNumbers.get(i);
                int second = rowNumbers.get(i + 1);
                if (second != first + 1 || (first % 2 == 0)) {
                    throw new IllegalArgumentException("Couple seats must be selected in pairs.");
                }
            }
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private Showtime findShowtime(Long showtimeId) {
        return showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new IllegalArgumentException("Showtime not found: " + showtimeId));
    }
}
