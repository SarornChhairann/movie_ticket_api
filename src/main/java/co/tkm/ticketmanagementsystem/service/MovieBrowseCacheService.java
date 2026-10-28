package co.tkm.ticketmanagementsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The movie-browse cache (1h TTL). With {@code app.redis.enabled} it lives in Redis as
 * JSON under {@value #PREFIX}, so every API instance shares it and an admin edit on one
 * evicts it for all; otherwise, or while Redis cannot be reached, it is this instance's
 * memory. Any write that changes what the browse endpoints return must call
 * {@link #evictAll()}.
 */
@Slf4j
@Service
public class MovieBrowseCacheService {

    static final Duration TTL = Duration.ofHours(1);
    static final String PREFIX = "movie-browse:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Map<String, CacheEntry> local = new ConcurrentHashMap<>();

    public MovieBrowseCacheService(ObjectProvider<StringRedisTemplate> redis, ObjectMapper objectMapper,
                                   @Value("${app.redis.enabled:false}") boolean redisEnabled) {
        this.redis = redisEnabled && redis != null ? redis.getIfAvailable() : null;
        this.objectMapper = objectMapper;
    }

    /** A memory-only cache (no Redis), for tests and single-node runs. */
    public static MovieBrowseCacheService inMemory() {
        return new MovieBrowseCacheService(null, null, false);
    }

    public <T> T getOrLoad(String key, TypeReference<T> type, Supplier<T> loader) {
        if (redis != null) {
            try {
                String json = redis.opsForValue().get(PREFIX + key);
                if (json != null) {
                    return objectMapper.readValue(json, type);
                }
                T loaded = loader.get();
                redis.opsForValue().set(PREFIX + key, objectMapper.writeValueAsString(loaded), TTL);
                return loaded;
            } catch (DataAccessException | JsonProcessingException ex) {
                log.warn("Redis browse cache unavailable ({}); using memory.", ex.getMessage());
            }
        }
        return getOrLoadLocal(key, loader);
    }

    public void evictAll() {
        local.clear();
        if (redis == null) return;
        try {
            List<String> keys = new ArrayList<>();
            try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(PREFIX + "*").count(500).build())) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (DataAccessException ex) {
            log.warn("Could not evict the Redis browse cache ({}); entries expire within {}.", ex.getMessage(), TTL);
        }
    }

    private <T> T getOrLoadLocal(String key, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        CacheEntry existing = local.get(key);
        if (existing != null && (now - existing.createdAtMillis) < TTL.toMillis()) {
            @SuppressWarnings("unchecked")
            T value = (T) existing.value;
            return value;
        }
        T loaded = loader.get();
        local.put(key, new CacheEntry(now, loaded));
        return loaded;
    }

    private record CacheEntry(long createdAtMillis, Object value) {
    }
}
