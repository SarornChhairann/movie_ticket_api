package co.tkm.ticketmanagementsystem.model;

import co.tkm.ticketmanagementsystem.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Payment extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @NotBlank
    @Column(nullable = false, length = 40)
    private String method;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    private LocalDateTime paidAt;

    /** PayWay transaction id (max 20 chars); null for the in-app gateway. */
    @Column(unique = true, length = 20)
    private String tranId;

    /** The {@code X-Session-Id} that started a PayWay payment: guest ownership and hold release. */
    @Column(length = 200)
    private String sessionId;

    /** Comma-separated showtime seat ids the PayWay payment pays for. */
    @Column(length = 1000)
    private String seatIds;

    /** When the seat hold behind a PayWay payment ends. */
    private LocalDateTime expiresAt;

    /** Last status PayWay reported (APPROVED, DECLINED, CANCELLED, callback status, CLOSED). */
    @Column(length = 30)
    private String providerStatus;
}
