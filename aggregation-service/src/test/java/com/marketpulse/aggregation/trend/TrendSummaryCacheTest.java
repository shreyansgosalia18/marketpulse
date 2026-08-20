package com.marketpulse.aggregation.trend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

/**
 * Redis is mocked here specifically to prove failure handling - a real
 * round-trip against live Redis is CachingIntegrationTest's job.
 */
class TrendSummaryCacheTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());

    @Test
    @SuppressWarnings("unchecked")
    void getReturnsEmptyRatherThanThrowingWhenRedisOperationFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("trend:AAPL")).thenThrow(new RuntimeException("connection refused"));
        TrendSummaryCache cache = new TrendSummaryCache(redisTemplate, objectMapper);

        Optional<TrendSummary> result = cache.get("AAPL");

        assertThat(result).isEmpty();
    }

    @Test
    void putAndEvictSwallowRedisFailuresRatherThanThrowing() {
        // Throwing from opsForValue() itself (rather than picking one of
        // ValueOperations.set()'s several overloads) exercises the same
        // try/catch in TrendSummaryCache.put() without an ambiguous-method
        // compile error from stubbing an overloaded call with matchers.
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("connection refused"));
        when(redisTemplate.delete("trend:AAPL")).thenThrow(new RuntimeException("connection refused"));
        TrendSummaryCache cache = new TrendSummaryCache(redisTemplate, objectMapper);
        TrendSummary summary = new TrendSummary("AAPL", 100.0, Optional.empty(), Optional.empty(), Map.of());

        assertThatCode(() -> cache.put("AAPL", summary)).doesNotThrowAnyException();
        assertThatCode(() -> cache.evict("AAPL")).doesNotThrowAnyException();
    }
}
