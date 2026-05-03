package co.tkm.ticketmanagementsystem.model;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String genre;

    @Column(length = 120)
    private String director;

    @Column(length = 60)
    private String language;

    @Column(length = 20)
    private String format;

    @Column(name = "movie_cast", columnDefinition = "text")
    private String cast;

    @Column(length = 500)
    private String trailerUrl;

    @Column(length = 10)
    private String rating;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer durationMinutes;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MovieStatus status;

    @Column(length = 500)
    private String imagePath;

    @Column(length = 500)
    private String bannerPath;
}
