package com.marketpulse.aggregation.trend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Live against real Redis and Postgres (TrendStore needs both). Skipped
 * automatically if either isn't reachable.
 */
@SpringBootTest
class CachingIntegrationTest {

    @Autowired
    private TrendStore trendStore;

    @Autowired
    private TrendSummaryCache cache;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private String ticker;

    @BeforeAll
    static void checkRedisAndPostgresReachable() {
        Assumptions.assumeTrue(isReachable("127.0.0.1", 6379), "Redis not reachable at 127.0.0.1:6379");
        Assumptions.assumeTrue(isReachable("127.0.0.1", 5432), "Postgres not reachable at 127.0.0.1:5432");
    }

    private static boolean isReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    @AfterEach
    void cleanUp() {
        if (ticker == null) {
            return;
        }
        cache.evict(ticker);
        jdbcTemplate.update("DELETE FROM price_bars WHERE ticker = :ticker", new MapSqlParameterSource("ticker", ticker));
        jdbcTemplate.update(
                "DELETE FROM sentiment_scores WHERE ticker = :ticker", new MapSqlParameterSource("ticker", ticker));
    }

    @Test
    void putThenGetRoundTripsCorrectly() {
        ticker = newTestTicker();
        TrendSummary summary =
                new TrendSummary(ticker, 123.45, Optional.of(6.7), Optional.of(0.42), Map.of("positive", 3L, "negative", 1L));

        cache.put(ticker, summary);
        Optional<TrendSummary> retrieved = cache.get(ticker);

        assertThat(retrieved).contains(summary);
    }

    @Test
    void cachedValueIsReturnedEvenWhenPostgresHasNoMatchingData() {
        ticker = newTestTicker();
        // Deliberately never written to Postgres - if this comes back, it can only be from the cache.
        TrendSummary fabricated = new TrendSummary(ticker, 999.99, Optional.empty(), Optional.empty(), Map.of());
        cache.put(ticker, fabricated);

        Optional<TrendSummary> summary = trendStore.getTrendSummary(ticker);

        assertThat(summary).contains(fabricated);
    }

    @Test
    void recordingNewDataEvictsStaleCacheEntry() {
        ticker = newTestTicker();
        TrendSummary stale = new TrendSummary(ticker, 1.0, Optional.empty(), Optional.empty(), Map.of());
        cache.put(ticker, stale);

        trendStore.recordPriceBar(new PriceBarRecord(ticker, LocalDate.of(2024, 1, 1), 50, 50, 50, 50, 100));

        Optional<TrendSummary> summary = trendStore.getTrendSummary(ticker);
        assertThat(summary).isPresent();
        assertThat(summary.get().latestClose()).isEqualTo(50.0);
        assertThat(summary.get()).isNotEqualTo(stale);
    }

    private static String newTestTicker() {
        return "TEST" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
