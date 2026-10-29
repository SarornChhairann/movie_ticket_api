package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Forgot password end to end, with the emailed code captured from {@link EmailService}. */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private EmailService emailService;

    @Test
    void codeResetsPasswordOnceAndSignsOutOldSessions() throws Exception {
        String email = register();
        String oldRefresh = loginRefreshToken(email, "secret1");

        forgot(email);
        String code = sentCode(email, 1);

        // A wrong code fails without using up the right one.
        reset(email, wrongCode(code), "newsecret")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_CODE_INVALID"));

        reset(email, code, "newsecret").andExpect(status().isNoContent());
        reset(email, code, "another1").andExpect(status().isBadRequest());

        login(email, "secret1").andExpect(status().isUnauthorized());
        login(email, "newsecret").andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailLooksTheSameAndSendsNothing() throws Exception {
        forgot("nobody-" + UUID.randomUUID() + "@example.com");
        verify(emailService, never()).sendPasswordResetCode(anyString(), any(), anyString(), any());
    }

    @Test
    void secondRequestWithinAMinuteIsNotResent() throws Exception {
        String email = register();
        forgot(email);
        forgot(email);
        sentCode(email, 1);
    }

    @Test
    void tooManyWrongGuessesLockTheCode() throws Exception {
        String email = register();
        forgot(email);
        String code = sentCode(email, 1);
        for (int i = 0; i < 10; i++) {
            reset(email, wrongCode(code), "newsecret").andExpect(status().isBadRequest());
        }
        reset(email, code, "newsecret").andExpect(status().isBadRequest());
        login(email, "secret1").andExpect(status().isOk());
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        String email = register();
        forgot(email);
        reset(email, sentCode(email, 1), "123").andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- helpers

    private String register() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reset User\",\"email\":\"" + email + "\",\"password\":\"secret1\"}"))
                .andExpect(status().isCreated());
        return email;
    }

    private void forgot(String email) throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
    }

    private org.springframework.test.web.servlet.ResultActions reset(String email, String code, String password)
            throws Exception {
        return mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"newPassword\":\"" + password + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String loginRefreshToken(String email, String password) throws Exception {
        String body = login(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.refresh_token");
    }

    private String sentCode(String email, int expectedSends) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(expectedSends))
                .sendPasswordResetCode(eq(email), any(), code.capture(), any(Duration.class));
        return code.getValue();
    }

    private static String wrongCode(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }
}
