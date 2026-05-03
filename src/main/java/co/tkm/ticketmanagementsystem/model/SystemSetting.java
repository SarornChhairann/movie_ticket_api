package co.tkm.ticketmanagementsystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "system_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SystemSetting {

    @Id
    @Column(name = "setting_key", nullable = false, length = 80)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 255)
    private String value;

    @Column(length = 300)
    private String description;
}
