package co.tkm.ticketmanagementsystem.model;

import co.tkm.ticketmanagementsystem.enums.Role;
import co.tkm.ticketmanagementsystem.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User extends AuditableEntity {

    /**
     * Stored in the (non-null) password column of an account that has no password yet.
     * It is not a BCrypt hash, so no login can ever match it.
     */
    public static final String NO_PASSWORD = "!";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String name;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(length = 30)
    private String phone;

    /** BCrypt hash, or {@link #NO_PASSWORD}. Never serialized. */
    @NotBlank
    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.CUSTOMER;

    @Column(nullable = false)
    private int points = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @PrePersist
    public void applyDefaultRole() {
        if (role == null) role = Role.CUSTOMER;
        if (status == null) status = UserStatus.ACTIVE;
    }
}
