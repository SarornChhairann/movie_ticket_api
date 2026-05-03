package co.tkm.ticketmanagementsystem.controller;

import co.tkm.ticketmanagementsystem.service.FoodItemImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/admin/api/food-items")
@RequiredArgsConstructor
public class FoodItemImageApiController {

    private final FoodItemImageStorageService foodItemImageStorageService;

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ImageUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        String imageUrl = foodItemImageStorageService.store(file);
        return ResponseEntity.created(URI.create(imageUrl)).body(new ImageUploadResponse(imageUrl));
    }

    @DeleteMapping("/images")
    public ResponseEntity<Void> delete(@RequestParam("imageUrl") String imageUrl) {
        foodItemImageStorageService.deleteIfManaged(imageUrl);
        return ResponseEntity.noContent().build();
    }

    public record ImageUploadResponse(String imageUrl) {
    }
}
