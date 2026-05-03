package co.tkm.ticketmanagementsystem.dto;

import co.tkm.ticketmanagementsystem.model.FoodItem;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import co.tkm.ticketmanagementsystem.model.User;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
public class CheckoutData {
    private final User user;
    private final Showtime showtime;
    private final List<ShowtimeSeat> selectedSeats;
    private final BigDecimal baseAmount;
    private final BigDecimal morningDiscount;
    private final BigDecimal weekendSurcharge;
    private final BigDecimal serviceFee;
    private final BigDecimal taxAmount;
    private final BigDecimal discountAmount;
    private final BigDecimal totalAmount;
    private final String promoCode;
    private final List<FoodItem> availableFoodItems;
    // foodItemId -> quantity selected (populated during confirm)
    private final Map<Long, Integer> foodSelections;

    public CheckoutData(User user, Showtime showtime, List<ShowtimeSeat> selectedSeats,
                        BigDecimal baseAmount, BigDecimal morningDiscount, BigDecimal weekendSurcharge,
                        BigDecimal serviceFee, BigDecimal taxAmount,
                        BigDecimal discountAmount, BigDecimal totalAmount,
                        String promoCode, List<FoodItem> availableFoodItems,
                        Map<Long, Integer> foodSelections) {
        this.user = user;
        this.showtime = showtime;
        this.selectedSeats = selectedSeats;
        this.baseAmount = baseAmount;
        this.morningDiscount = morningDiscount;
        this.weekendSurcharge = weekendSurcharge;
        this.serviceFee = serviceFee;
        this.taxAmount = taxAmount;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
        this.promoCode = promoCode;
        this.availableFoodItems = availableFoodItems;
        this.foodSelections = foodSelections;
    }
}
