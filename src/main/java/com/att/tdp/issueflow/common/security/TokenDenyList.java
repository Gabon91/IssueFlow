package com.att.tdp.issueflow.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * In-memory deny-list keyed by JWT {@code jti}. Each entry's TTL equals the token's
 * remaining lifetime, so the cache cannot grow without bound. Pluggable to Redis later.
 */
@Component
public class TokenDenyList {

    private final Cache<String, Instant> cache = Caffeine.newBuilder()
        .expireAfter(new Expiry<String, Instant>() {
            @Override public long expireAfterCreate(String key, Instant exp, long currentTime) {
                return nanosUntil(exp);
            }
            @Override public long expireAfterUpdate(String key, Instant exp, long currentTime, long currentDuration) {
                return nanosUntil(exp);
            }
            @Override public long expireAfterRead(String key, Instant exp, long currentTime, long currentDuration) {
                return currentDuration;
            }
        })
        .maximumSize(100_000)
        .build();

    public void revoke(String jti, Instant expiresAt) {
        cache.put(jti, expiresAt);
    }

    public boolean isRevoked(String jti) {
        return cache.getIfPresent(jti) != null;
    }

    private static long nanosUntil(Instant exp) {
        long millis = Math.max(0, exp.toEpochMilli() - System.currentTimeMillis());
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }
}
