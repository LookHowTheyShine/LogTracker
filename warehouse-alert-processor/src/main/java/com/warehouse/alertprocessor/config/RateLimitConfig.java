package com.warehouse.alertprocessor.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    /**
     * Reuses the Lettuce RedisClient that Spring Boot auto-configured.
     * Bucket4j needs a StatefulRedisConnection<String, byte[]> — the byte[]
     * value codec is required because Bucket4j serialises bucket state as bytes.
     */
    @Bean
    public StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(
            LettuceConnectionFactory connectionFactory) {

        if (!(connectionFactory.getNativeClient() instanceof RedisClient redisClient)) {
            throw new IllegalStateException(
                    "Expected a standalone Lettuce RedisClient; cluster mode not supported yet.");
        }
        return redisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    /**
     * ProxyManager manages one Bucket4j bucket per Redis key.
     * Expiry is set to 2× the refill period so idle-device keys are cleaned up.
     */
    @Bean
    public LettuceBasedProxyManager<String> rateLimitProxyManager(
            StatefulRedisConnection<String, byte[]> rateLimitRedisConnection) {

        return LettuceBasedProxyManager
                .builderFor(rateLimitRedisConnection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
                .build();
    }
}
