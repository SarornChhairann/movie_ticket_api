package co.tkm.ticketmanagementsystem.model;

import co.tkm.ticketmanagementsystem.enums.DiscountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "promo_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PromoCode extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DiscountType discountType = DiscountType.PERCENT;

    @NotNull
    @Min(1)
    @Max(100)
    @Column(nullable = false)
    private Integer discountPercent;

    @DecimalMin("0.0")
    @Column(precision = 10, scale = 2)
    private BigDecimal minPurchase;

    @Column(nullable = true)
    private LocalDate validUntil;

    @Column(nullable = false)
    private int maxUses = 0; // 0 = unlimited

    @Column(nullable = false)
    private int usedCount = 0;

    @Column(nullable = false)
    private boolean active = true;
}
