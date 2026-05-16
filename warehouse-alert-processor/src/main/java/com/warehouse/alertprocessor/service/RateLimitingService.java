package com.warehouse.alertprocessor.service;

import com.warehouse.alertprocessor.model.WarehouseErrorEvent;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class RateLimitingService {

    static final String RATE_LIMIT_KEY_PREFIX = "warehouse:ratelimit:";
    static final String RATE_LIMIT_COUNT_KEY  = "warehouse:stats:rate_limited";

    private final LettuceBasedProxyManager<String> proxyManager;
    private final StringRedisTemplate redisTemplate;
    private final BucketConfiguration bucketConfig;

    public RateLimitingService(
            LettuceBasedProxyManager<String> proxyManager,
            StringRedisTemplate redisTemplate,
            @Value("${warehouse.alert.rate-limit-per-minute:10}") int ratePerMinute) {

        this.proxyManager   = proxyManager;
        this.redisTemplate  = redisTemplate;
        this.bucketConfig   = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(ratePerMinute)
                        .refillGreedy(ratePerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /**
     * Returns true if this event should be suppressed.
     *
     * Each deviceId gets its own bucket in Redis (key: warehouse:ratelimit:{deviceId}).
     * Capacity is 10 tokens, refilled fully every minute (greedy strategy — tokens
     * trickle in continuously, not all at once at the minute boundary).
     *
     * Fails open: if Redis is unreachable the event is allowed through.
     */
    public boolean isRateLimited(WarehouseErrorEvent event) {
        String key = RATE_LIMIT_KEY_PREFIX + event.getDeviceId();
        try {
            Bucket bucket = proxyManager.builder().build(key, () -> bucketConfig);
            boolean limited = !bucket.tryConsume(1);
            if (limited) {
                redisTemplate.opsForValue().increment(RATE_LIMIT_COUNT_KEY);
                log.debug("Rate-limited: deviceId={} key={}", event.getDeviceId(), key);
            }
            return limited;
        } catch (Exception e) {
            log.warn("Rate limit check failed for deviceId={} — allowing through: {}",
                    event.getDeviceId(), e.getMessage());
            return false;
        }
    }

    /** Available tokens remaining for a device (for debugging / dashboard). */
    public long availableTokens(String deviceId) {
        try {
            Bucket bucket = proxyManager.builder().build(
                    RATE_LIMIT_KEY_PREFIX + deviceId, () -> bucketConfig);
            return bucket.getAvailableTokens();
        } catch (Exception e) {
            return -1L;
        }
    }

    /** Total rate-limited events since last Redis restart. Used by the dashboard API. */
    public long getRateLimitedCount() {
        try {
            String val = redisTemplate.opsForValue().get(RATE_LIMIT_COUNT_KEY);
            return val != null ? Long.parseLong(val) : 0L;
        } catch (Exception e) {
            return -1L;
        }
    }
}
