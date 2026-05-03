package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.FoodItem;
import co.tkm.ticketmanagementsystem.repository.FoodItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodItemService {

    private final FoodItemRepository foodItemRepository;
    private final FoodItemImageStorageService foodItemImageStorageService;

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
        return saveWithImage(foodItem, null);
    }

    @Transactional
    public void delete(Long id) {
        FoodItem existing = findById(id);
        String imageUrlToDelete = existing.getImageUrl();
        foodItemRepository.delete(existing);
        deleteAfterCommit(imageUrlToDelete);
    }

    @Transactional
    public FoodItem saveWithImage(FoodItem input, MultipartFile imageFile) {
        FoodItem target = input;
        String previousImageUrl = null;

        if (input.getId() != null) {
            FoodItem existing = findById(input.getId());
            previousImageUrl = existing.getImageUrl();

            existing.setName(input.getName());
            existing.setDescription(input.getDescription());
            existing.setPrice(input.getPrice());
            existing.setAvailable(input.isAvailable());
            existing.setStockQuantity(input.getStockQuantity());
            existing.setImageUrl(input.getImageUrl());
            target = existing;
        }

        if (target.getImageUrl() == null) {
            target.setImageUrl(previousImageUrl);
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            String stored = foodItemImageStorageService.store(imageFile);
            target.setImageUrl(stored);
        }

        FoodItem saved = foodItemRepository.save(target);

        if (previousImageUrl != null
                && !previousImageUrl.isBlank()
                && (saved.getImageUrl() == null || !saved.getImageUrl().equals(previousImageUrl))) {
            deleteAfterCommit(previousImageUrl);
        }

        return saved;
    }

    private void deleteAfterCommit(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            foodItemImageStorageService.deleteIfManaged(imageUrl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                foodItemImageStorageService.deleteIfManaged(imageUrl);
            }
        });
    }
}
