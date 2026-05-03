package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByOrderBySentAtDesc();
}
