package co.tkm.ticketmanagementsystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FoodItemImageStorageService {

    public static final String UPLOAD_URL_PREFIX = "/uploads/food-items/";

    private static final Map<String, String> CONTENT_TYPE_TO_EXTENSION = Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );

    @Value("${app.uploads.food-items-dir:uploads/food-items}")
    private String uploadsFoodItemsDir;

    @Value("${app.uploads.food-items.max-bytes:5242880}")
    private long maxBytes;

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FoodItemImageStorageException(FoodItemImageStorageException.Type.INVALID_FORMAT, "No image file provided.");
        }
        if (file.getSize() > maxBytes) {
            throw new FoodItemImageStorageException(
                    FoodItemImageStorageException.Type.FILE_TOO_LARGE,
                    "Image is too large. Max allowed is " + maxBytes + " bytes."
            );
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new FoodItemImageStorageException(FoodItemImageStorageException.Type.INVALID_FORMAT, "Unknown image content type.");
        }

        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        String extension = CONTENT_TYPE_TO_EXTENSION.get(normalizedType);
        if (extension == null) {
            throw new FoodItemImageStorageException(
                    FoodItemImageStorageException.Type.INVALID_FORMAT,
                    "Unsupported image format. Allowed: JPG, PNG, WEBP, GIF."
            );
        }

        LocalDate today = LocalDate.now();
        String year = String.valueOf(today.getYear());
        String month = String.format("%02d", today.getMonthValue());
        String filename = "fooditem_" + today.toString().replace("-", "") + "_" + UUID.randomUUID() + extension;

        Path root = Paths.get(uploadsFoodItemsDir).toAbsolutePath().normalize();
        Path folder = root.resolve(year).resolve(month).normalize();

        try {
            Files.createDirectories(folder);
            Path destination = folder.resolve(filename).normalize();
            if (!destination.startsWith(root)) {
                throw new FoodItemImageStorageException(FoodItemImageStorageException.Type.INVALID_PATH, "Invalid storage path.");
            }
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new FoodItemImageStorageException(FoodItemImageStorageException.Type.STORAGE_FAILED, "Failed to store image.", ex);
        }

        return UPLOAD_URL_PREFIX + year + "/" + month + "/" + filename;
    }

    public boolean deleteIfManaged(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }
        if (!imageUrl.startsWith(UPLOAD_URL_PREFIX)) {
            return false;
        }

        String relative = imageUrl.substring(UPLOAD_URL_PREFIX.length());
        Path root = Paths.get(uploadsFoodItemsDir).toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new FoodItemImageStorageException(FoodItemImageStorageException.Type.INVALID_PATH, "Invalid image path.");
        }

        try {
            return Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new FoodItemImageStorageException(FoodItemImageStorageException.Type.STORAGE_FAILED, "Failed to delete image.", ex);
        }
    }
}
