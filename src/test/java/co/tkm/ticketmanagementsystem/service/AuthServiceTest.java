package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.enums.Role;
import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import co.tkm.ticketmanagementsystem.security.KeycloakClaims;
import co.tkm.ticketmanagementsystem.service.KeycloakAdminClient.TokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private KeycloakAdminClient keycloak;
    @Mock private UserService userService;
    @Mock private JwtDecoder jwtDecoder;

    @InjectMocks
    private AuthService authService;

    private static Jwt jwtFor(String email) {
        return Jwt.withTokenValue("access")
                .header("alg", "none")
                .claim("email", email)
                .claim("name", "Dara Sok")
                .build();
    }

    private static User userWithStatus(UserStatus status) {
        User user = new User();
        user.setEmail("dara@example.com");
        user.setRole(Role.CUSTOMER);
        user.setStatus(status);
        return user;
    }

    @Test
    void login_rejectsSuspendedUserWithAccountBlockedCode() {
        when(keycloak.passwordGrant("dara@example.com", "secret"))
                .thenReturn(new TokenResponse("access", "refresh", 300, "Bearer"));
        when(jwtDecoder.decode("access")).thenReturn(jwtFor("dara@example.com"));
        when(userService.mirrorKeycloakUser(eq("dara@example.com"), anyString(), eq(Role.CUSTOMER)))
                .thenReturn(userWithStatus(UserStatus.SUSPENDED));

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.login(" Dara@Example.com ", "secret"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo(AuthService.ACCOUNT_BLOCKED);
        assertThat(ex.getMessage()).isEqualTo("This account is suspended.");
    }

    @Test
    void login_returnsSessionForActiveUser() {
        when(keycloak.passwordGrant("dara@example.com", "secret"))
                .thenReturn(new TokenResponse("access", "refresh", 300, "Bearer"));
        when(jwtDecoder.decode("access")).thenReturn(jwtFor("dara@example.com"));
        User active = userWithStatus(UserStatus.ACTIVE);
        when(userService.mirrorKeycloakUser(eq("dara@example.com"), anyString(), eq(Role.CUSTOMER)))
                .thenReturn(active);

        AuthService.Session session = authService.login("dara@example.com", "secret");

        assertThat(session.user()).isSameAs(active);
        assertThat(session.token().refreshToken()).isEqualTo("refresh");
    }

    @Test
    void register_createsCustomerInKeycloakAndMirrorsAsCustomer() {
        when(keycloak.createUser(any(), any(), any(), any(), any(), any())).thenReturn("kc-id");
        when(userService.mirrorKeycloakUser(any(), any(), any())).thenReturn(userWithStatus(UserStatus.ACTIVE));

        authService.register("Dara Sok", "Dara@Example.com", "secret1");

        verify(keycloak).createUser("dara@example.com", "dara@example.com", "Dara", "Sok",
                "secret1", KeycloakClaims.CUSTOMER_REALM_ROLE);
        verify(userService).mirrorKeycloakUser("dara@example.com", "Dara Sok", Role.CUSTOMER);
    }

    @Test
    void mirrorKeycloakUser_newAccountDefaultsToActiveCustomer() {
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        new UserService(userRepository).mirrorKeycloakUser("new@example.com", "New", Role.CUSTOMER);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getValue().getPassword()).isEqualTo(User.KEYCLOAK_MANAGED_PASSWORD);
    }
}
