package co.tkm.ticketmanagementsystem.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    @SuppressWarnings("unchecked")
    void withRedisTheMissesAreASharedSortedSetThatExpiresWithTheWindow() {
        ManualClock clock = new ManualClock();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        when(zset.zCard("lookup-failures:1.2.3.4")).thenReturn(5L);
        LookupRateLimiter limiter = new LookupRateLimiter(clock, redis);

        limiter.recordFailure("1.2.3.4");
        assertThat(limiter.isBlocked("1.2.3.4")).isTrue();

        long now = clock.instant().toEpochMilli();
        verify(zset).add(eq("lookup-failures:1.2.3.4"), anyString(), eq((double) now));
        verify(redis).expire("lookup-failures:1.2.3.4", LookupRateLimiter.WINDOW);
        verify(zset).removeRangeByScore("lookup-failures:1.2.3.4", Double.NEGATIVE_INFINITY,
                (double) clock.instant().minus(LookupRateLimiter.WINDOW).toEpochMilli());
    }

    @Test
    void whileRedisIsDownTheThrottleStillWorksInMemory() {
        ManualClock clock = new ManualClock();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForZSet()).thenThrow(new RedisConnectionFailureException("down"));
        LookupRateLimiter limiter = new LookupRateLimiter(clock, redis);

        for (int i = 0; i < LookupRateLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure("1.2.3.4");
        }
        assertThat(limiter.isBlocked("1.2.3.4")).isTrue();
    }
}
