package co.tkm.ticketmanagementsystem.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles guessing on {@code POST /bookings/lookup}: after {@link #MAX_FAILURES} misses
 * from one client inside {@link #WINDOW}, every lookup from it is refused until the
 * oldest miss ages out. Hits are not counted, so a device refreshing its own saved
 * bookings is never locked out. With {@code app.redis.enabled} the misses are a Redis
 * sorted set per client (scored by time), shared by every API instance; without Redis,
 * or while it cannot be reached, they are kept in this instance's memory.
 */
@Slf4j
@Component
public class LookupRateLimiter {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(10);
    static final String PREFIX = "lookup-failures:";

    private final Clock clock;
    private final StringRedisTemplate redis;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    public LookupRateLimiter(ObjectProvider<StringRedisTemplate> redis,
                             @Value("${app.redis.enabled:false}") boolean redisEnabled) {
        this(Clock.systemUTC(), redisEnabled ? redis.getIfAvailable() : null);
    }

    LookupRateLimiter(Clock clock) {
        this(clock, null);
    }

    LookupRateLimiter(Clock clock, StringRedisTemplate redis) {
        this.clock = clock;
        this.redis = redis;
    }

    public boolean isBlocked(String client) {
        if (redis != null) {
            try {
                String key = PREFIX + client;
                redis.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, cutoff().toEpochMilli());
                Long count = redis.opsForZSet().zCard(key);
                return count != null && count >= MAX_FAILURES;
            } catch (DataAccessException ex) {
                log.warn("Redis lookup throttle unavailable ({}); using memory.", ex.getMessage());
            }
        }
        Deque<Instant> misses = failures.get(client);
        if (misses == null) return false;
        synchronized (misses) {
            prune(misses);
            return misses.size() >= MAX_FAILURES;
        }
    }

    public void recordFailure(String client) {
        if (redis != null) {
            try {
                String key = PREFIX + client;
                Instant now = clock.instant();
                // A unique member per miss: two misses in the same millisecond both count.
                redis.opsForZSet().add(key, now.toEpochMilli() + ":" + UUID.randomUUID(), now.toEpochMilli());
                redis.expire(key, WINDOW);
                return;
            } catch (DataAccessException ex) {
                log.warn("Redis lookup throttle unavailable ({}); using memory.", ex.getMessage());
            }
        }
        Deque<Instant> misses = failures.computeIfAbsent(client, k -> new ArrayDeque<>());
        synchronized (misses) {
            prune(misses);
            misses.addLast(clock.instant());
        }
    }

    private Instant cutoff() {
        return clock.instant().minus(WINDOW);
    }

    private void prune(Deque<Instant> misses) {
        Instant cutoff = cutoff();
        while (!misses.isEmpty() && !misses.peekFirst().isAfter(cutoff)) {
            misses.pollFirst();
        }
    }
}
