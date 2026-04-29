package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.dto.CheckoutData;
import co.tkm.ticketmanagementsystem.enums.BookingStatus;
import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.BookingRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeSeatRepository;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final TicketService ticketService;

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
                                   String guestName, String guestPhone) {
        CheckoutData checkoutData = resolveCheckoutData(showtimeId, selectedSeatIds, userEmail, true);

        Booking booking = new Booking();
        booking.setUser(checkoutData.getUser());

        if (checkoutData.getUser() == null) {
            booking.setGuestName(guestName);
            booking.setGuestPhone(guestPhone);
        }

        booking.setShowtime(checkoutData.getShowtime());
        booking.setTotalAmount(checkoutData.getTotalAmount());
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookedAt(LocalDateTime.now());
        Booking savedBooking = bookingRepository.save(booking);

        paymentService.processMockPayment(savedBooking);
        ticketService.issueTickets(savedBooking, checkoutData.getSelectedSeats());
        return savedBooking;
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

        BigDecimal total = BigDecimal.ZERO;

        for (ShowtimeSeat seat : selectedSeats) {
            if (!seat.getShowtime().getId().equals(showtimeId)) {
                throw new IllegalArgumentException("Selected seats must belong to the chosen showtime.");
            }
            if (seat.getStatus() != ShowtimeSeatStatus.AVAILABLE) {
                throw new IllegalArgumentException("Some seats were already booked. Please reselect seats.");
            }

            if (seat.getSeat().getSeatType() == SeatType.COUPLE) {
                total = total.add(showtime.getCouplePrice());
            } else {
                total = total.add(showtime.getNormalPrice());
            }
        }

        showtime.getMovie().getTitle();
        showtime.getHall().getName();
        selectedSeats.forEach(seat -> seat.getSeat().getSeatCode());
        return new CheckoutData(user, showtime, selectedSeats, total);
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
