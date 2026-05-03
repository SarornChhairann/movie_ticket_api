package co.tkm.ticketmanagementsystem.repository;

import co.tkm.ticketmanagementsystem.model.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
