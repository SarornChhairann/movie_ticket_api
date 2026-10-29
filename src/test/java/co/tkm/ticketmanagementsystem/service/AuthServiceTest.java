package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.enums.Role;
import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import co.tkm.ticketmanagementsystem.service.TokenService.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    @Mock private UserRepository userRepository;
    @Mock private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, ENCODER, tokenService);
    }

    private static User user(UserStatus status, String password) {
        User user = new User();
        user.setId(7L);
        user.setEmail("dara@example.com");
        user.setName("Dara Sok");
        user.setRole(Role.CUSTOMER);
        user.setStatus(status);
        user.setPassword(password == null ? User.NO_PASSWORD : ENCODER.encode(password));
        return user;
    }

    @Test
    void login_rejectsSuspendedUserWithAccountBlockedCode() {
        when(userRepository.findByEmail("dara@example.com"))
                .thenReturn(Optional.of(user(UserStatus.SUSPENDED, "secret")));

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.login(" Dara@Example.com ", "secret"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo(AuthService.ACCOUNT_BLOCKED);
        assertThat(ex.getMessage()).isEqualTo("This account is suspended.");
        verify(tokenService, never()).issue(any());
    }

    @Test
    void login_returnsSessionForActiveUser() {
        User active = user(UserStatus.ACTIVE, "secret");
        when(userRepository.findByEmail("dara@example.com")).thenReturn(Optional.of(active));
        when(tokenService.issue(active)).thenReturn(new TokenResponse("access", "refresh", 900, "Bearer"));

        AuthService.Session session = authService.login("dara@example.com", "secret");

        assertThat(session.user()).isSameAs(active);
        assertThat(session.token().refreshToken()).isEqualTo("refresh");
    }

    @Test
    void login_rejectsWrongPassword() {
        when(userRepository.findByEmail("dara@example.com"))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE, "secret")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login("dara@example.com", "wrong"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_rejectsAccountWithoutPassword() {
        when(userRepository.findByEmail("dara@example.com"))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE, null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login("dara@example.com", "!"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void register_storesActiveCustomerWithHashedPassword() {
        when(userRepository.findByEmail("dara@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register("Dara Sok", "Dara@Example.com", "secret1");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("dara@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(ENCODER.matches("secret1", saved.getValue().getPassword())).isTrue();
    }

    @Test
    void register_rejectsTakenEmail() {
        when(userRepository.findByEmail("dara@example.com"))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE, "secret")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.register("Dara", "dara@example.com", "secret1"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void changePassword_revokesEverySession() {
        User active = user(UserStatus.ACTIVE, "secret");
        when(userRepository.findByEmail("dara@example.com")).thenReturn(Optional.of(active));

        authService.changePassword("dara@example.com", "secret", "newsecret");

        assertThat(ENCODER.matches("newsecret", active.getPassword())).isTrue();
        verify(tokenService).revokeAll(7L);
    }

    @Test
    void changePassword_rejectsWrongCurrentPassword() {
        when(userRepository.findByEmail("dara@example.com"))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE, "secret")));

        assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword("dara@example.com", "wrong", "newsecret"));
        verify(tokenService, never()).revokeAll(any());
    }
}
