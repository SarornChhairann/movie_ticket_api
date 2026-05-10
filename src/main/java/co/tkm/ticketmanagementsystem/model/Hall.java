package co.tkm.ticketmanagementsystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "halls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Hall {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Min(1)
    @Column(nullable = false)
    private Integer totalSeats = 60;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id")
    private Cinema cinema;

    @Column(length = 100)
    private String city;

    @Column(length = 300)
    private String address;

    @Column(length = 40)
    private String type;

    @Column
    private Integer capacity;

    @Column(name = "has_dolby_atmos", nullable = false)
    private boolean hasDolbyAtmos = false;

    @Column(name = "has_screen_x", nullable = false)
    private boolean hasScreenX = false;

    @Column(columnDefinition = "text")
    private String amenities;
}
