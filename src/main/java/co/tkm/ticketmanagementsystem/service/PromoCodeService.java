package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.PromoCode;
import co.tkm.ticketmanagementsystem.repository.PromoCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;

    @Transactional(readOnly = true)
    public List<PromoCode> findAll() {
        return promoCodeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public PromoCode findById(Long id) {
        return promoCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promo code not found: " + id));
    }

    @Transactional
    public PromoCode save(PromoCode promoCode) {
        return promoCodeRepository.save(promoCode);
    }

    @Transactional
    public void delete(Long id) {
        promoCodeRepository.deleteById(id);
    }

    /**
     * Validates a promo code. Returns the discount percent (for PERCENT type)
     * or a synthetic percent computed from FIXED amount relative to the subtotal.
     * Throws IllegalArgumentException if invalid/expired/exhausted/below min purchase.
     */
    @Transactional(readOnly = true)
    public int validate(String code) {
        return validateWithSubtotal(code, null);
    }

    @Transactional(readOnly = true)
    public int validateWithSubtotal(String code, java.math.BigDecimal subtotal) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Promo code is empty.");
        }
        PromoCode promo = promoCodeRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new IllegalArgumentException("Promo code not found."));

        if (!promo.isActive()) {
            throw new IllegalArgumentException("Promo code is no longer active.");
        }
        if (promo.getValidUntil() != null && promo.getValidUntil().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Promo code has expired.");
        }
        if (promo.getMaxUses() > 0 && promo.getUsedCount() >= promo.getMaxUses()) {
            throw new IllegalArgumentException("Promo code has reached its usage limit.");
        }
        if (promo.getMinPurchase() != null && subtotal != null
                && subtotal.compareTo(promo.getMinPurchase()) < 0) {
            throw new IllegalArgumentException(
                    "Minimum purchase of $" + promo.getMinPurchase() + " required for this promo code.");
        }
        return promo.getDiscountPercent();
    }

    /**
     * Increments the usage count for a promo code after successful booking.
     */
    @Transactional
    public void recordUsage(String code) {
        if (code == null || code.isBlank()) return;
        promoCodeRepository.findByCodeIgnoreCase(code.trim()).ifPresent(promo -> {
            promo.setUsedCount(promo.getUsedCount() + 1);
            promoCodeRepository.save(promo);
        });
    }
}
