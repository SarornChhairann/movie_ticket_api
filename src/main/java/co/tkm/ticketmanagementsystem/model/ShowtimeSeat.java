package co.tkm.ticketmanagementsystem.model;

import co.tkm.ticketmanagementsystem.enums.ShowtimeSeatStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "showtime_seats", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"showtime_id", "seat_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShowtimeSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShowtimeSeatStatus status;
}
