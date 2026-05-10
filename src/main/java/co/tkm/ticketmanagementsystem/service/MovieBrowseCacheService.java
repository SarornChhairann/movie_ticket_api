package co.tkm.ticketmanagementsystem.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class MovieBrowseCacheService {

    private static final long TTL_MILLIS = Duration.ofHours(1).toMillis();

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public <T> T getOrLoad(String key, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        CacheEntry existing = cache.get(key);
        if (existing != null && (now - existing.createdAtMillis) < TTL_MILLIS) {
            @SuppressWarnings("unchecked")
            T value = (T) existing.value;
            return value;
        }
        T loaded = loader.get();
        cache.put(key, new CacheEntry(now, loaded));
        return loaded;
    }

    public void evictAll() {
        cache.clear();
    }

    private record CacheEntry(long createdAtMillis, Object value) {
    }
}

