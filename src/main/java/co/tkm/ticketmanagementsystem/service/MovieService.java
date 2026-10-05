package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import co.tkm.ticketmanagementsystem.model.Movie;
import co.tkm.ticketmanagementsystem.repository.MovieRepository;
import co.tkm.ticketmanagementsystem.repository.ShowtimeRepository;
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
    private final ShowtimeRepository showtimeRepository;
    private final MovieBrowseCacheService movieBrowseCacheService;
    @Value("${app.uploads.movies-dir:uploads/movies}")
    private String uploadsMoviesDir;

    @Transactional
    public Movie save(Movie movie) {
        Movie saved = movieRepository.save(movie);
        movieBrowseCacheService.evictAll();
        return saved;
    }

    @Transactional
    public Movie saveWithImage(Movie movie, MultipartFile imageFile) throws IOException {
        return saveWithImages(movie, imageFile, null);
    }

    @Transactional
    public Movie saveWithImages(Movie movie, MultipartFile imageFile, MultipartFile bannerFile) throws IOException {
        Movie target = movie;
        if (movie.getId() != null) {
            Movie existing = findById(movie.getId());
            if (movie.getImagePath() == null || movie.getImagePath().isBlank()) {
                movie.setImagePath(existing.getImagePath());
            }
            if (movie.getBannerPath() == null || movie.getBannerPath().isBlank()) {
                movie.setBannerPath(existing.getBannerPath());
            }
            target = existing;
            target.setTitle(movie.getTitle());
            target.setDescription(movie.getDescription());
            target.setGenre(movie.getGenre());
            target.setDirector(movie.getDirector());
            target.setLanguage(movie.getLanguage());
            target.setFormat(movie.getFormat());
            target.setCast(movie.getCast());
            target.setTrailerUrl(movie.getTrailerUrl());
            target.setRating(movie.getRating());
            target.setReleaseDate(movie.getReleaseDate());
            target.setDurationMinutes(movie.getDurationMinutes());
            target.setStatus(movie.getStatus());
            target.setImagePath(movie.getImagePath());
            target.setBannerPath(movie.getBannerPath());
        }

        if (hasImageFile(imageFile)) {
            validateImageContentType(imageFile);
            target.setImagePath(storeImage(imageFile));
        }
        if (hasImageFile(bannerFile)) {
            validateImageContentType(bannerFile);
            target.setBannerPath(storeImage(bannerFile));
        }

        Movie saved = movieRepository.save(target);
        movieBrowseCacheService.evictAll();
        return saved;
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

    @Transactional(readOnly = true)
    public java.util.Optional<Movie> findFeaturedMovie() {
        return movieRepository.findFirstByStatusAndImagePathIsNotNullOrderByIdDesc(MovieStatus.NOW_SHOWING);
    }

    @Transactional(readOnly = true)
    public List<Movie> findComingSoon() {
        return movieRepository.findTop6ByStatusOrderByIdDesc(MovieStatus.COMING_SOON);
    }

    @Transactional(readOnly = true)
    public List<Movie> filterMovies(MovieStatus status, String genre, String language, String format, String rating, String search) {
        String g = (genre == null) ? "" : genre.trim();
        String l = (language == null) ? "" : language.trim();
        String f = (format == null || format.isBlank()) ? null : format.trim();
        String r = (rating == null) ? "" : rating.trim();
        String s = (search == null) ? "" : search.trim();
        return movieRepository.findFiltered(status, g, l, f, r, s);
    }

    @Transactional(readOnly = true)
    public List<String> findDistinctGenres() {
        return movieRepository.findDistinctGenres();
    }

    @Transactional(readOnly = true)
    public List<String> findDistinctLanguages() {
        return movieRepository.findDistinctLanguages();
    }

    @Transactional
    public Movie update(Long id, Movie data) {
        Movie movie = findById(id);
        movie.setTitle(data.getTitle());
        movie.setDescription(data.getDescription());
        movie.setGenre(data.getGenre());
        movie.setLanguage(data.getLanguage());
        movie.setFormat(data.getFormat());
        movie.setCast(data.getCast());
        movie.setTrailerUrl(data.getTrailerUrl());
        movie.setRating(data.getRating());
        movie.setReleaseDate(data.getReleaseDate());
        movie.setDurationMinutes(data.getDurationMinutes());
        movie.setStatus(data.getStatus());
        Movie saved = movieRepository.save(movie);
        movieBrowseCacheService.evictAll();
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        long showtimes = showtimeRepository.countByMovieId(id);
        if (showtimes > 0) {
            throw new IllegalStateException("Cannot delete movie: it has " + showtimes
                    + " showtime(s). Delete its showtimes first, or set the movie to ARCHIVED.");
        }
        movieRepository.deleteById(id);
        movieBrowseCacheService.evictAll();
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
