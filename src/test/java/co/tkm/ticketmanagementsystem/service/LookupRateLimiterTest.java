package co.tkm.ticketmanagementsystem.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LookupRateLimiterTest {

    /** A clock the test moves by hand. */
    private static final class ManualClock extends Clock {
        private Instant now = Instant.parse("2026-10-06T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public Instant instant() { return now; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    @Test
    void blocksAfterFiveMissesUntilTheOldestIsTenMinutesOld() {
        ManualClock clock = new ManualClock();
        LookupRateLimiter limiter = new LookupRateLimiter(clock);

        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("1.2.3.4");
            clock.advance(Duration.ofMinutes(1));
        }
        assertThat(limiter.isBlocked("1.2.3.4")).isFalse();

        limiter.recordFailure("1.2.3.4");
        assertThat(limiter.isBlocked("1.2.3.4")).isTrue();
        assertThat(limiter.isBlocked("5.6.7.8")).isFalse();

        // The first miss was at 10:00; at 10:10 it no longer counts.
        clock.advance(Duration.ofMinutes(6));
        assertThat(limiter.isBlocked("1.2.3.4")).isFalse();
    }
}
