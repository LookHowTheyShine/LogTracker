package com.warehouse.alertprocessor.service;

import com.warehouse.alertprocessor.model.WarehouseErrorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private final StringRedisTemplate redisTemplate;

    @Value("${warehouse.alert.dedup-ttl-seconds:60}")
    private long dedupTtlSeconds;

    static final String DEDUP_KEY_PREFIX = "warehouse:dedup:";
    static final String DEDUP_COUNT_KEY  = "warehouse:stats:deduped";

    /**
     * Returns true if this event is a duplicate.
     *
     * Uses a single atomic SET NX (setIfAbsent) so there are no race conditions
     * under concurrent consumers. The key expires after dedupTtlSeconds (default 60s),
     * which resets the suppression window.
     *
     * Fails open: if Redis is unreachable the event is treated as new (not suppressed).
     */
    public boolean isDuplicate(WarehouseErrorEvent event) {
        String key = DEDUP_KEY_PREFIX + event.dedupKey();
        try {
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(dedupTtlSeconds));

            boolean duplicate = Boolean.FALSE.equals(isNew);
            if (duplicate) {
                redisTemplate.opsForValue().increment(DEDUP_COUNT_KEY);
                log.debug("Duplicate suppressed: key={}", key);
            }
            return duplicate;
        } catch (Exception e) {
            log.warn("Redis dedup unavailable for key={} — treating as non-duplicate: {}", key, e.getMessage());
            return false;
        }
    }

    /** Total deduplicated events since last Redis restart. Used by dashboard API. */
    public long getDedupCount() {
        try {
            String val = redisTemplate.opsForValue().get(DEDUP_COUNT_KEY);
            return val != null ? Long.parseLong(val) : 0L;
        } catch (Exception e) {
            log.warn("Could not read dedup count: {}", e.getMessage());
            return -1L;
        }
    }

    /** Remaining TTL in seconds for a given dedup key (useful for debugging). */
    public long getTtlSeconds(WarehouseErrorEvent event) {
        try {
            Long ttl = redisTemplate.getExpire(DEDUP_KEY_PREFIX + event.dedupKey());
            return ttl != null ? ttl : -2L;
        } catch (Exception e) {
            return -2L;
        }
    }
}
