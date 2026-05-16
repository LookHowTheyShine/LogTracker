package com.warehouse.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStatsService {

    private static final String KEY_DEDUPED       = "warehouse:stats:deduped";
    private static final String KEY_RATE_LIMITED   = "warehouse:stats:rate_limited";

    private final StringRedisTemplate redisTemplate;

    public long getDedupedCount() {
        return parseLong(redisTemplate.opsForValue().get(KEY_DEDUPED));
    }

    public long getRateLimitedCount() {
        return parseLong(redisTemplate.opsForValue().get(KEY_RATE_LIMITED));
    }

    public boolean isRedisUp() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }

    private long parseLong(String value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
