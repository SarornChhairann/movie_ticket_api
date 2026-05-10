package co.tkm.ticketmanagementsystem.web;

import co.tkm.ticketmanagementsystem.controller.CheckInController;
import co.tkm.ticketmanagementsystem.model.Ticket;
import co.tkm.ticketmanagementsystem.service.TicketService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ui.ConcurrentModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class CheckInControllerFlashTest {

    @Test
    void processCheckIn_disablesGlobalFlash_onEmptyCode() {
        TicketService ticketService = Mockito.mock(TicketService.class);
        CheckInController controller = new CheckInController(ticketService);
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.processCheckIn("   ", model);

        assertEquals("admin/checkin", view);
        assertEquals(false, model.getAttribute("globalFlash"));
        assertEquals("Please enter a ticket code.", model.getAttribute("error"));
    }

    @Test
    void processCheckIn_disablesGlobalFlash_onSuccess() {
        TicketService ticketService = Mockito.mock(TicketService.class);
        when(ticketService.checkIn("ABC")).thenReturn(new Ticket());

        CheckInController controller = new CheckInController(ticketService);
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.processCheckIn("ABC", model);

        assertEquals("admin/checkin", view);
        assertEquals(false, model.getAttribute("globalFlash"));
        assertTrue(model.containsAttribute("ticket"));
        assertEquals("Check-in successful!", model.getAttribute("success"));
    }
}

