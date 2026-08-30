package co.tkm.ticketmanagementsystem.controller.admin;

import co.tkm.ticketmanagementsystem.model.PromoCode;
import co.tkm.ticketmanagementsystem.service.PromoCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/promo-codes")
@RequiredArgsConstructor
@Tag(name = "Admin: Promo Codes", description = "Back-office promo code management")
public class AdminPromoCodeController {

    private final PromoCodeService promoCodeService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List all promo codes")
    public ResponseEntity<List<PromoCode>> promoCodes() {
        return ResponseEntity.ok(promoCodeService.findAll());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a promo code by id")
    public ResponseEntity<PromoCode> promoCode(@PathVariable Long id) {
        return ResponseEntity.ok(promoCodeService.findById(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create or update a promo code (set id to update)")
    public ResponseEntity<PromoCode> savePromoCode(@RequestBody PromoCode promoCode) {
        return ResponseEntity.ok(promoCodeService.save(promoCode));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a promo code")
    public ResponseEntity<Void> deletePromoCode(@PathVariable Long id) {
        promoCodeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
