package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.FoodItem;
import co.tkm.ticketmanagementsystem.repository.FoodItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodItemService {

    private final FoodItemRepository foodItemRepository;

    @Transactional(readOnly = true)
    public List<FoodItem> findAll() {
        return foodItemRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<FoodItem> findAvailable() {
        return foodItemRepository.findByAvailableTrue();
    }

    @Transactional(readOnly = true)
    public FoodItem findById(Long id) {
        return foodItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Food item not found: " + id));
    }

    @Transactional
    public FoodItem save(FoodItem foodItem) {
        return foodItemRepository.save(foodItem);
    }

    @Transactional
    public void delete(Long id) {
        foodItemRepository.deleteById(id);
    }
}
