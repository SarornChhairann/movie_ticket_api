package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.BookingFoodItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BookingFoodItemRepository extends JpaRepository<BookingFoodItem, Long> {

    List<BookingFoodItem> findByBooking(Booking booking);

    @Query("""
        SELECT bfi FROM BookingFoodItem bfi
        WHERE bfi.booking.showtime.showDate = :date
        ORDER BY bfi.booking.showtime.showTime ASC, bfi.foodItem.name ASC
        """)
    List<BookingFoodItem> findByShowtimeDate(@Param("date") LocalDate date);
}
