package co.tkm.ticketmanagementsystem.controller.admin;

import co.tkm.ticketmanagementsystem.model.Booking;
import co.tkm.ticketmanagementsystem.model.BookingFoodItem;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.repository.BookingFoodItemRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/admin/food-orders")
@RequiredArgsConstructor
@Tag(name = "Admin: Food Orders", description = "Back-office food order reporting")
public class AdminFoodOrderController {

    private final BookingFoodItemRepository bookingFoodItemRepository;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List food orders for a given date (defaults to today)")
    @Transactional(readOnly = true)
    public ResponseEntity<List<FoodOrderResponse>> foodOrders(@RequestParam(required = false) String date) {
        LocalDate targetDate = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        List<BookingFoodItem> orders = bookingFoodItemRepository.findByShowtimeDate(targetDate);
        List<FoodOrderResponse> response = orders.stream().map(o -> {
            Booking booking = o.getBooking();
            Showtime showtime = booking.getShowtime();
            String customerName = booking.getUser() != null ? booking.getUser().getName() : booking.getGuestName();
            return new FoodOrderResponse(o.getId(), booking.getId(), customerName,
                    showtime.getMovie().getTitle(), showtime.getShowDate(), showtime.getShowTime(),
                    o.getFoodItem().getName(), o.getQuantity(), o.getUnitPrice());
        }).toList();
        return ResponseEntity.ok(response);
    }

    public record FoodOrderResponse(Long id, Long bookingId, String customerName, String movieTitle,
                                     LocalDate showDate, LocalTime showTime, String foodItemName,
                                     Integer quantity, BigDecimal unitPrice) {
    }
}
