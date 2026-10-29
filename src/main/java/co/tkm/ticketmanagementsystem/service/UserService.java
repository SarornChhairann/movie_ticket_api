package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.UserStatus;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Profile, status and loyalty-points operations on the {@code users} table.
 * Credentials (registration, login, password changes) live in {@link AuthService}.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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
