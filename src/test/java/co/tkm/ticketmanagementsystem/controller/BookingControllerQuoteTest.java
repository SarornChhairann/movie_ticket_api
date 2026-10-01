package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.dto.CheckoutData;
import co.tkm.ticketmanagementsystem.model.Hall;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.model.Showtime;
import co.tkm.ticketmanagementsystem.service.BookingService;
import co.tkm.ticketmanagementsystem.service.PromoCodeService;
import co.tkm.ticketmanagementsystem.service.SeatHoldService;
import co.tkm.ticketmanagementsystem.service.SeatRealtimeService;
import co.tkm.ticketmanagementsystem.service.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingControllerQuoteTest {

    private static final String SESSION = "3f2b8c1e-6a4d-4c3b-9e2f-1a2b3c4d5e6f";

    @Mock private BookingService bookingService;
    @Mock private TicketService ticketService;
    @Mock private SeatHoldService seatHoldService;
    @Mock private SeatRealtimeService seatRealtimeService;
    @Mock private PromoCodeService promoCodeService;

    @InjectMocks
    private BookingController controller;

    private static CheckoutData sampleQuote() {
        Movie movie = new Movie();
        movie.setTitle("Dune");
        Hall hall = new Hall();
        hall.setId(1L);
        hall.setName("Hall 1");
        Showtime showtime = new Showtime();
        showtime.setId(50L);
        showtime.setMovie(movie);
        showtime.setHall(hall);
        showtime.setShowDate(LocalDate.of(2026, 10, 7));
        showtime.setShowTime(LocalTime.of(14, 0));
        showtime.setNormalPrice(new BigDecimal("5.00"));
        showtime.setCouplePrice(new BigDecimal("8.00"));
        BigDecimal z = BigDecimal.ZERO;
        return new CheckoutData(null, showtime, List.of(), new BigDecimal("10.00"), z, z, new BigDecimal("10.00"),
                new BigDecimal("1.50"), new BigDecimal("1.00"), z, new BigDecimal("12.50"), z, List.of(),
                null, List.of(), Map.of());
    }

    @Test
    void quote_pricesTheHeldSeatsWithoutHoldingOrBooking() {
        when(seatHoldService.ownsActiveHolds(List.of(1L, 2L), SESSION)).thenReturn(true);
        when(bookingService.buildCheckout(50L, List.of(1L, 2L), null, "SAVE10", Map.of(7L, 2)))
                .thenReturn(sampleQuote());

        BookingController.QuoteResponse body = controller.quote(50L,
                new BookingController.QuoteRequest(List.of(1L, 2L), "SAVE10", Map.of(7L, 2)),
                null, SESSION, new MockHttpServletRequest()).getBody();

        assertThat(body.subtotal()).isEqualByComparingTo("10.00");
        assertThat(body.totalAmount()).isEqualByComparingTo("12.50");
        assertThat(body.showtime().movieTitle()).isEqualTo("Dune");
        verify(seatHoldService, never()).holdSeatsRealtime(any(), anyList(), any());
        verify(bookingService, never()).confirmCheckout(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(seatRealtimeService);
    }

    @Test
    void quote_withoutAnActiveHoldIsHoldExpired() {
        when(seatHoldService.ownsActiveHolds(List.of(1L), SESSION)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> controller.quote(50L,
                new BookingController.QuoteRequest(List.of(1L), null, null),
                null, SESSION, new MockHttpServletRequest()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(SeatHoldService.HOLD_EXPIRED);
        verifyNoInteractions(bookingService);
    }
}
