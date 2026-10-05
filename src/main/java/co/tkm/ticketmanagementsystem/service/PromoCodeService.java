package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.DiscountType;
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
        validateForSave(promoCode);
        // usedCount is owned by checkout (recordUsage); a save never sets or resets it.
        int usedCount = promoCode.getId() == null ? 0 : findById(promoCode.getId()).getUsedCount();
        promoCode.setUsedCount(usedCount);
        return promoCodeRepository.save(promoCode);
    }

    @Transactional
    public void delete(Long id) {
        promoCodeRepository.deleteById(id);
    }

    /** The entity's bean constraints, checked up front so a bad body is a 400, not a failed flush. */
    private void validateForSave(PromoCode p) {
        if (p.getCode() == null || p.getCode().isBlank()) {
            throw new IllegalArgumentException("Promo code is required.");
        }
        p.setCode(p.getCode().trim());
        if (p.getCode().length() > 50) {
            throw new IllegalArgumentException("Promo code must be at most 50 characters.");
        }
        if (p.getDiscountType() == null) {
            p.setDiscountType(DiscountType.PERCENT);
        }
        if (p.getDiscountPercent() == null || p.getDiscountPercent() < 1 || p.getDiscountPercent() > 100) {
            throw new IllegalArgumentException("Discount percent must be between 1 and 100.");
        }
        if (p.getMinPurchase() != null && p.getMinPurchase().signum() < 0) {
            throw new IllegalArgumentException("Minimum purchase cannot be negative.");
        }
        if (p.getMaxUses() < 0) {
            throw new IllegalArgumentException("Max uses cannot be negative (0 means unlimited).");
        }
        promoCodeRepository.findByCodeIgnoreCase(p.getCode())
                .filter(other -> !other.getId().equals(p.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Promo code \"" + p.getCode() + "\" already exists.");
                });
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
