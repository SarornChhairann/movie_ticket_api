package co.tkm.ticketmanagementsystem.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BookingReferenceTest {

    @Test
    void generatesEightCharactersWithoutLookAlikes() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            String ref = BookingReference.generate();
            assertThat(ref).hasSize(8).matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}");
            seen.add(ref);
        }
        assertThat(seen).hasSize(2_000);
    }

    @Test
    void normalizeIgnoresCaseSpacesAndDashes() {
        assertThat(BookingReference.normalize(" ab3d-ef 7h ")).isEqualTo("AB3DEF7H");
        assertThat(BookingReference.normalize(" - ")).isNull();
    }

    @Test
    void cambodianPhoneFormatsCompareEqual() {
        String local = BookingReference.phoneDigits("012 345 678");
        assertThat(BookingReference.phoneDigits("+855 12-345 678")).isEqualTo(local);
        assertThat(BookingReference.phoneDigits("85512345678")).isEqualTo(local);
        assertThat(BookingReference.phoneDigits("012 345 679")).isNotEqualTo(local);
        assertThat(BookingReference.phoneDigits(null)).isEmpty();
    }
}
