package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.Notification;
import co.tkm.ticketmanagementsystem.repository.NotificationRepository;
import co.tkm.ticketmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Notification> findAll() {
        return notificationRepository.findAllByOrderBySentAtDesc();
    }

    @Transactional
    public Notification sendBroadcast(String title, String message) {
        long userCount = userRepository.count();
        Notification n = new Notification();
        n.setTitle(title);
        n.setMessage(message);
        n.setTargetType("ALL");
        n.setSentAt(LocalDateTime.now());
        n.setSentCount((int) userCount);
        return notificationRepository.save(n);
    }

    @Transactional
    public void delete(Long id) {
        notificationRepository.deleteById(id);
    }
}
