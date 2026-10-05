package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.PromoCode;
import co.tkm.ticketmanagementsystem.repository.PromoCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoCodeServiceSaveTest {

    @Mock private PromoCodeRepository promoCodeRepository;
    @InjectMocks private PromoCodeService promoCodeService;

    @Test
    void updateKeepsStoredUsedCountWhateverTheBodySays() {
        PromoCode stored = new PromoCode();
        stored.setId(7L);
        stored.setUsedCount(12);
        when(promoCodeRepository.findById(7L)).thenReturn(Optional.of(stored));
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(inv -> inv.getArgument(0));

        PromoCode edit = new PromoCode();
        edit.setId(7L);
        edit.setCode("SAVE10");
        edit.setDiscountPercent(15);
        edit.setUsedCount(0);

        assertEquals(12, promoCodeService.save(edit).getUsedCount());
    }

    @Test
    void percentOutsideOneToHundredIsRejected() {
        for (int bad : new int[]{0, 101}) {
            PromoCode p = new PromoCode();
            p.setCode("BAD");
            p.setDiscountPercent(bad);
            assertThrows(IllegalArgumentException.class, () -> promoCodeService.save(p));
        }
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void duplicateCodeIgnoringCaseIsRejected() {
        PromoCode existing = new PromoCode();
        existing.setId(1L);
        existing.setCode("SAVE10");
        when(promoCodeRepository.findByCodeIgnoreCase("save10")).thenReturn(Optional.of(existing));

        PromoCode p = new PromoCode();
        p.setCode(" save10 ");
        p.setDiscountPercent(10);

        assertThrows(IllegalArgumentException.class, () -> promoCodeService.save(p));
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void createStartsUsedCountAtZero() {
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(inv -> inv.getArgument(0));

        PromoCode created = new PromoCode();
        created.setCode("SAVE10");
        created.setDiscountPercent(10);
        created.setUsedCount(99);

        assertEquals(0, promoCodeService.save(created).getUsedCount());
    }
}
