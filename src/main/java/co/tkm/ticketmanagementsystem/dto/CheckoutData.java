package co.tkm.ticketmanagementsystem.dto;

import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class CheckoutData {
    private final User user;
    private final Showtime showtime;
    private final List<ShowtimeSeat> selectedSeats;
    private final BigDecimal totalAmount;
}
