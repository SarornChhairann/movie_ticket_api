package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {
    List<FoodItem> findByAvailableTrue();
}
