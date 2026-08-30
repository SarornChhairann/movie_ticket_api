package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.Role;
import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The local {@code users} table. Since Keycloak took over authentication this table
 * is a mirror of the realm rather than a credential store: it exists so bookings,
 * tickets, points and loyalty have a row to point at, and it is kept up to date from
 * token claims by {@link AuthService} and {@code KeycloakUserSyncFilter}.
 *
 * Two columns are still owned here rather than in Keycloak, because they are
 * application state and not identity: {@code points} and {@code status}.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Creates or refreshes the local row for a Keycloak account. Called on login and
     * on every authenticated request, so it writes only when something actually changed.
     *
     * The user's name is taken from Keycloak on creation only - afterwards
     * {@code POST /profile} owns it locally and pushes changes back to Keycloak, so
     * re-reading it here would undo the edit on the next request.
     */
    @Transactional
    public User mirrorKeycloakUser(String email, String name, Role role) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isEmpty()) {
            User user = new User();
            user.setEmail(email);
            user.setName((name == null || name.isBlank()) ? email : name);
            user.setPassword(User.KEYCLOAK_MANAGED_PASSWORD);
            user.setRole(role == null ? Role.CUSTOMER : role);
            user.setStatus(UserStatus.ACTIVE);
            return userRepository.save(user);
        }

        User user = existing.get();
        if (role != null && user.getRole() != role) {
            user.setRole(role);
            return userRepository.save(user);
        }
        return user;
    }

    @Transactional
    public User createAdminIfMissing(String name, String email) {
        return userRepository.findByEmail(email).map(existing -> {
            existing.setRole(Role.ADMIN);
            existing.setName(name);
            existing.setStatus(UserStatus.ACTIVE);
            return userRepository.save(existing);
        }).orElseGet(() -> {
            User admin = new User();
            admin.setName(name);
            admin.setEmail(email);
            admin.setPassword(User.KEYCLOAK_MANAGED_PASSWORD);
            admin.setRole(Role.ADMIN);
            return userRepository.save(admin);
        });
    }

    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    @Transactional
    public void updateProfile(String email, String name, String phone) {
        User user = findByEmail(email);
        if (name != null && !name.isBlank()) {
            user.setName(name.trim());
        }
        user.setPhone(phone != null ? phone.trim() : null);
        userRepository.save(user);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public List<User> search(String query) {
        return userRepository.searchUsers(query == null ? "" : query);
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Transactional
    public void setStatus(Long userId, UserStatus status) {
        User user = findById(userId);
        user.setStatus(status);
        userRepository.save(user);
    }

    @Transactional
    public void adjustPoints(Long userId, int delta) {
        User user = findById(userId);
        int updated = user.getPoints() + delta;
        user.setPoints(Math.max(0, updated));
        userRepository.save(user);
    }
}
