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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public static final String MAX_SEATS_PER_BOOKING   = "max_seats_per_booking";
    public static final String HOLD_EXTEND_MINUTES     = "hold_extend_minutes";

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
            seed(HOLD_EXTEND_MINUTES,    "5",    "Minutes one extend adds to a seat hold (once per hold)");
            seed(MAX_SEATS_PER_BOOKING,  "8",    "Most seats one session may hold or book at once (a couple pair counts as 2)");
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

    /**
     * Validates every entry first, then saves them together, so one bad value saves nothing.
     * Only the known keys are accepted; values must parse the way the readers parse them.
     */
    @Transactional
    public void saveAll(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("No settings to save.");
        }
        List<String> problems = new ArrayList<>();
        Map<String, String> clean = new LinkedHashMap<>();
        values.forEach((key, raw) -> {
            String value = raw == null ? "" : raw.trim();
            String problem = checkValue(key, value);
            if (problem != null) problems.add(problem);
            else clean.put(key, value);
        });
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", problems));
        }
        clean.forEach(this::set);
    }

    private static String checkValue(String key, String value) {
        switch (key) {
            case HOLD_MINUTES -> {
                Integer n = parseWhole(value);
                return (n == null || n < 1) ? "hold_minutes must be a whole number of at least 1." : null;
            }
            case HOLD_EXTEND_MINUTES -> {
                Integer n = parseWhole(value);
                return (n == null || n < 1) ? "hold_extend_minutes must be a whole number of at least 1." : null;
            }
            case MAX_SEATS_PER_BOOKING -> {
                Integer n = parseWhole(value);
                return (n == null || n < 1) ? "max_seats_per_booking must be a whole number of at least 1." : null;
            }
            case CANCELLATION_HOURS -> {
                Integer n = parseWhole(value);
                return (n == null || n < 0 || n > 72) ? "cancellation_hours must be a whole number from 0 to 72." : null;
            }
            case SERVICE_FEE -> {
                BigDecimal d = parseDecimal(value);
                return (d == null || d.signum() < 0) ? "service_fee must be an amount of 0 or more, e.g. 1.50." : null;
            }
            case TAX_RATE, WEEKEND_SURCHARGE_RATE, MORNING_DISCOUNT_RATE -> {
                BigDecimal d = parseDecimal(value);
                return (d == null || d.signum() < 0 || d.compareTo(BigDecimal.ONE) > 0)
                        ? key + " must be a decimal from 0 to 1 (0.10 means 10%)." : null;
            }
            default -> {
                return "Unknown setting: " + key + ".";
            }
        }
    }

    private static Integer parseWhole(String v) {
        try { return Integer.valueOf(v); } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal parseDecimal(String v) {
        try { return new BigDecimal(v); } catch (NumberFormatException e) { return null; }
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
