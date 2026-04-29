package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MovieService {

    private static final String UPLOAD_URL_PREFIX = "/uploads/movies/";

    private final MovieRepository movieRepository;
    @Value("${app.uploads.movies-dir:uploads/movies}")
    private String uploadsMoviesDir;

    @Transactional
    public Movie save(Movie movie) {
        return movieRepository.save(movie);
    }

    @Transactional
    public Movie saveWithImage(Movie movie, MultipartFile imageFile) throws IOException {
        Movie target = movie;
        if (movie.getId() != null) {
            Movie existing = findById(movie.getId());
            if (movie.getImagePath() == null || movie.getImagePath().isBlank()) {
                movie.setImagePath(existing.getImagePath());
            }
            target = existing;
            target.setTitle(movie.getTitle());
            target.setDescription(movie.getDescription());
            target.setGenre(movie.getGenre());
            target.setDurationMinutes(movie.getDurationMinutes());
            target.setStatus(movie.getStatus());
            target.setImagePath(movie.getImagePath());
        }

        if (hasImageFile(imageFile)) {
            validateImageContentType(imageFile);
            target.setImagePath(storeImage(imageFile));
        }

        return movieRepository.save(target);
    }

    public List<Movie> findAll() {
        return movieRepository.findAll();
    }

    public Movie findById(Long id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Movie not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Movie> findByStatus(MovieStatus status) {
        return movieRepository.findByStatus(status);
    }

    @Transactional
    public Movie update(Long id, Movie data) {
        Movie movie = findById(id);
        movie.setTitle(data.getTitle());
        movie.setDescription(data.getDescription());
        movie.setGenre(data.getGenre());
        movie.setDurationMinutes(data.getDurationMinutes());
        movie.setStatus(data.getStatus());
        return movieRepository.save(movie);
    }

    @Transactional
    public void delete(Long id) {
        movieRepository.deleteById(id);
    }

    public long count() {
        return movieRepository.count();
    }

    private boolean hasImageFile(MultipartFile imageFile) {
        return imageFile != null && !imageFile.isEmpty();
    }

    private void validateImageContentType(MultipartFile imageFile) {
        String contentType = imageFile.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed.");
        }
    }

    private String storeImage(MultipartFile imageFile) throws IOException {
        Path uploadPath = Paths.get(uploadsMoviesDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        String originalName = imageFile.getOriginalFilename();
        String safeName = originalName == null ? "movie" : originalName.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
        int dot = safeName.lastIndexOf('.');
        String extension = dot >= 0 ? safeName.substring(dot) : "";
        String filename = UUID.randomUUID() + extension;

        Path destination = uploadPath.resolve(filename);
        Files.copy(imageFile.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        return UPLOAD_URL_PREFIX + filename;
    }
}
