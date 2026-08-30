package co.tkm.ticketmanagementsystem.controller.admin;

import co.tkm.ticketmanagementsystem.model.FoodItem;
import co.tkm.ticketmanagementsystem.service.FoodItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/admin/food-items")
@RequiredArgsConstructor
@Tag(name = "Admin: Food Items", description = "Back-office food item management")
public class AdminFoodItemController {

    private final FoodItemService foodItemService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List all food items")
    public ResponseEntity<List<FoodItem>> foodItems() {
        return ResponseEntity.ok(foodItemService.findAll());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a food item by id")
    public ResponseEntity<FoodItem> foodItem(@PathVariable Long id) {
        return ResponseEntity.ok(foodItemService.findById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create or update a food item (set id to update); image is an optional multipart part")
    public ResponseEntity<FoodItem> saveFoodItem(@ModelAttribute FoodItem foodItem,
                               @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) {
        return ResponseEntity.ok(foodItemService.saveWithImage(foodItem, imageFile));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a food item")
    public ResponseEntity<Void> deleteFoodItem(@PathVariable Long id) {
        foodItemService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
