package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.Role;
import co.tkm.ticketmanagementsystem.model.User;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User registerCustomer(String name, String email, String rawPassword) {
        userRepository.findByEmail(email).ifPresent(existing -> {
            throw new IllegalArgumentException("Email is already in use.");
        });

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.CUSTOMER);
        return userRepository.save(user);
    }

    @Transactional
    public User createAdminIfMissing(String name, String email, String rawPassword) {
        return userRepository.findByEmail(email).map(existing -> {
            if (existing.getRole() != Role.ADMIN) {
                existing.setRole(Role.ADMIN);
            }
            existing.setName(name);
            existing.setPassword(passwordEncoder.encode(rawPassword));
            return userRepository.save(existing);
        }).orElseGet(() -> {
            User admin = new User();
            admin.setName(name);
            admin.setEmail(email);
            admin.setPassword(passwordEncoder.encode(rawPassword));
            admin.setRole(Role.ADMIN);
            return userRepository.save(admin);
        });
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }
}
