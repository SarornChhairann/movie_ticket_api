package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Register, log in, call a secured endpoint, refresh, log out - all against this app's own tokens. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    @Test
    void fullTokenLifecycle() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@example.com";

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Flow User\",\"email\":\"" + email + "\",\"password\":\"secret1\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        JsonNode login = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn().getResponse().getContentAsString());
        String access = login.get("access_token").asText();
        String refresh = login.get("refresh_token").asText();

        mvc.perform(get("/profile").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/admin/users").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
        mvc.perform(get("/profile").header("Authorization", "Bearer " + access + "x"))
                .andExpect(status().isUnauthorized());

        JsonNode refreshed = json(mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String newRefresh = refreshed.get("refresh_token").asText();

        // Refresh tokens are single-use.
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"" + newRefresh + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"" + newRefresh + "\"}"))
                .andExpect(status().isUnauthorized());

        // A suspended account is turned away even with an unexpired access token.
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);
        mvc.perform(get("/profile").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
    }

    @Test
    void configuredAdminCanLogInAsAdmin() throws Exception {
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@gmail.com\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
