package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.SystemSetting;
import co.tkm.ticketmanagementsystem.repository.SystemSettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingService.class);

    public static final String HOLD_MINUTES          = "hold_minutes";
    public static final String CANCELLATION_HOURS    = "cancellation_hours";
    public static final String SERVICE_FEE           = "service_fee";
    public static final String TAX_RATE              = "tax_rate";
    public static final String WEEKEND_SURCHARGE_RATE  = "weekend_surcharge_rate";
    public static final String MORNING_DISCOUNT_RATE   = "morning_discount_rate";

    private final SystemSettingRepository repo;

    @PostConstruct
    @Transactional
    public void seedDefaults() {
        try {
            seed(HOLD_MINUTES,           "10",   "Minutes a seat is held after selection");
            seed(CANCELLATION_HOURS,     "2",    "Hours before showtime within which cancellation is blocked");
            seed(SERVICE_FEE,            "1.50", "Flat service fee per booking (USD)");
            seed(TAX_RATE,               "0.10", "Tax rate as decimal (e.g. 0.10 = 10%)");
            seed(WEEKEND_SURCHARGE_RATE, "0.10", "Weekend surcharge rate as decimal (e.g. 0.10 = 10%)");
            seed(MORNING_DISCOUNT_RATE,  "0.10", "Morning discount rate for showtimes before noon (e.g. 0.10 = 10%)");
        } catch (Exception ex) {
            log.error("Failed to seed system settings defaults. Application can still start, but pricing/holds may break.", ex);
        }
    }

    private void seed(String key, String defaultValue, String description) {
        repo.findById(key).orElseGet(() -> {
            log.info("Seeding system setting: {}", key);
            return repo.save(new SystemSetting(key, defaultValue, description));
        });
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> findAll() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public String get(String key) {
        return repo.findById(key).map(SystemSetting::getValue).orElse(null);
    }

    @Transactional
    public void set(String key, String value) {
        SystemSetting s = repo.findById(key).orElse(new SystemSetting(key, value, null));
        s.setValue(value);
        repo.save(s);
    }

    public int getInt(String key, int defaultVal) {
        String v = get(key);
        try { return v != null ? Integer.parseInt(v.trim()) : defaultVal; }
        catch (NumberFormatException e) { return defaultVal; }
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultVal) {
        String v = get(key);
        try { return v != null ? new BigDecimal(v.trim()) : defaultVal; }
        catch (NumberFormatException e) { return defaultVal; }
    }
}
