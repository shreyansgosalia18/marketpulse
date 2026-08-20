package com.marketpulse.aggregation.trend;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Caches computed TrendSummary values by ticker. Postgres remains the
 * source of truth - every operation here is defensive: a Redis failure is
 * logged and treated as "the cache did nothing," never thrown, so a down
 * Redis degrades performance, not correctness (see
 * docs/user-stories/redis-caching-layer.md).
 *
 * <p>Primary consistency comes from TrendStore evicting on every write;
 * the TTL below is a safety net for a missed eviction, not the main
 * mechanism.
 */
@Component
public class TrendSummaryCache {

    private static final Logger log = LoggerFactory.getLogger(TrendSummaryCache.class);
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "trend:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TrendSummaryCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<TrendSummary> get(String ticker) {
        try {
            String json = redisTemplate.opsForValue().get(key(ticker));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, TrendSummary.class));
        } catch (Exception exception) {
            log.warn("Cache read failed for ticker {}, falling back to Postgres: {}", ticker, exception.toString());
            return Optional.empty();
        }
    }

    public void put(String ticker, TrendSummary summary) {
        try {
            String json = objectMapper.writeValueAsString(summary);
            redisTemplate.opsForValue().set(key(ticker), json, TTL);
        } catch (Exception exception) {
            log.warn("Cache write failed for ticker {}: {}", ticker, exception.toString());
        }
    }

    public void evict(String ticker) {
        try {
            redisTemplate.delete(key(ticker));
        } catch (Exception exception) {
            log.warn("Cache eviction failed for ticker {}: {}", ticker, exception.toString());
        }
    }

    private static String key(String ticker) {
        return KEY_PREFIX + ticker;
    }
}
