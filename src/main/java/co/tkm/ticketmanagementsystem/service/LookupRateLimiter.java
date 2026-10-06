package co.tkm.ticketmanagementsystem.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles guessing on {@code POST /bookings/lookup}: after {@link #MAX_FAILURES} misses
 * from one client inside {@link #WINDOW}, every lookup from it is refused until the
 * oldest miss ages out. Hits are not counted, so a device refreshing its own saved
 * bookings is never locked out. In memory, per instance: enough for one API node.
 */
@Component
public class LookupRateLimiter {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(10);

    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LookupRateLimiter() {
        this(Clock.systemUTC());
    }

    LookupRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String client) {
        Deque<Instant> misses = failures.get(client);
        if (misses == null) return false;
        synchronized (misses) {
            prune(misses);
            return misses.size() >= MAX_FAILURES;
        }
    }

    public void recordFailure(String client) {
        Deque<Instant> misses = failures.computeIfAbsent(client, k -> new ArrayDeque<>());
        synchronized (misses) {
            prune(misses);
            misses.addLast(clock.instant());
        }
    }

    private void prune(Deque<Instant> misses) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!misses.isEmpty() && !misses.peekFirst().isAfter(cutoff)) {
            misses.pollFirst();
        }
    }
}
