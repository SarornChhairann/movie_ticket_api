package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.SystemSetting;
import co.tkm.ticketmanagementsystem.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceSaveAllTest {

    @Mock private SystemSettingRepository repo;
    @InjectMocks private SystemSettingService service;

    private static Map<String, String> valid() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("hold_minutes", "10");
        m.put("cancellation_hours", "2");
        m.put("service_fee", "1.50");
        m.put("tax_rate", "0.10");
        m.put("weekend_surcharge_rate", "0.10");
        m.put("morning_discount_rate", "0.10");
        return m;
    }

    @Test
    void validSixKeysAreAllSaved() {
        when(repo.findById(anyString())).thenReturn(Optional.empty());

        service.saveAll(valid());

        verify(repo, times(6)).save(any(SystemSetting.class));
    }

    @Test
    void oneBadValueSavesNothing() {
        Map<String, String> m = valid();
        m.put("tax_rate", "10");          // percent, not decimal
        m.put("hold_minutes", "2.5");     // not whole

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.saveAll(m));

        assertTrue(ex.getMessage().contains("tax_rate"));
        assertTrue(ex.getMessage().contains("hold_minutes"));
        verify(repo, never()).save(any());
    }

    @Test
    void unknownKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.saveAll(Map.of("free_tickets", "1")));
        verify(repo, never()).save(any());
    }
}
