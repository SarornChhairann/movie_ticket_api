package co.tkm.ticketmanagementsystem.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HoldSessionsTest {

    @Test
    void usesTheHeaderWhenItIsAUuid() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String id = HoldSessions.resolve(" 3f2b8c1e-6a4d-4c3b-9e2f-1a2b3c4d5e6f ", request);

        assertThat(id).isEqualTo("3f2b8c1e-6a4d-4c3b-9e2f-1a2b3c4d5e6f");
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void rejectsAHeaderThatIsNotAUuid() {
        ApiException ex = assertThrows(ApiException.class,
                () -> HoldSessions.resolve("not-a-uuid", new MockHttpServletRequest()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(HoldSessions.SESSION_REQUIRED);
    }

    @Test
    void fallsBackToTheServletSessionForCookieClients() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String id = HoldSessions.resolve(null, request);

        assertThat(id).isEqualTo(request.getSession(false).getId());
    }

    @Test
    void holdExpiryEpochUsesTheJvmZone() {
        LocalDateTime inSevenMinutes = LocalDateTime.now().plusMinutes(7);

        long epoch = SeatHoldApiController.toEpochMs(inSevenMinutes);

        assertThat(epoch).isEqualTo(inSevenMinutes.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        assertThat(Math.abs(epoch - (System.currentTimeMillis() + 7 * 60_000L))).isLessThan(5_000L);
    }
}
