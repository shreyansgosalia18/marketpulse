package com.marketpulse.aggregation.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.marketpulse.aggregation.trend.PriceBarRecord;
import com.marketpulse.aggregation.trend.SentimentRecord;

/**
 * Live against the real local Postgres (see docs/reference/local-dev.md).
 * Skipped automatically if it isn't reachable.
 */
@SpringBootTest
class PersistenceIntegrationTest {

    @Autowired
    private PriceBarRepository priceBarRepository;

    @Autowired
    private SentimentScoreRepository sentimentScoreRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private String ticker;

    @BeforeAll
    static void checkPostgresReachable() {
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
    void cleanUpTestData() {
        if (ticker == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM price_bars WHERE ticker = :ticker", new MapSqlParameterSource("ticker", ticker));
        jdbcTemplate.update(
                "DELETE FROM sentiment_scores WHERE ticker = :ticker", new MapSqlParameterSource("ticker", ticker));
    }

    @Test
    void upsertingSamePriceBarKeyOverwritesRatherThanDuplicates() {
        ticker = newTestTicker();
        priceBarRepository.upsert(new PriceBarRecord(ticker, LocalDate.of(2024, 1, 1), 100, 101, 99, 100, 1000));
        // Redelivery with a corrected close - same (ticker, tradeDate) key.
        priceBarRepository.upsert(new PriceBarRecord(ticker, LocalDate.of(2024, 1, 1), 100, 101, 99, 102, 1000));

        List<PriceBarRecord> bars = priceBarRepository.findByTicker(ticker);

        assertThat(bars).hasSize(1);
        assertThat(bars.get(0).close()).isEqualTo(102.0);
    }

    @Test
    void upsertingSameSentimentKeyIsIdempotent() {
        ticker = newTestTicker();
        SentimentRecord record = new SentimentRecord(ticker, "article-1", "positive", 0.5, Instant.parse("2024-01-01T00:00:00Z"));
        sentimentScoreRepository.upsert(record);
        sentimentScoreRepository.upsert(record);

        List<SentimentRecord> records = sentimentScoreRepository.findByTicker(ticker);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).compoundScore()).isEqualTo(0.5);
    }

    @Test
    void findByTickerReturnsPriceBarsOrderedByDate() {
        ticker = newTestTicker();
        // Inserted out of order deliberately.
        priceBarRepository.upsert(new PriceBarRecord(ticker, LocalDate.of(2024, 1, 3), 1, 1, 1, 103, 1));
        priceBarRepository.upsert(new PriceBarRecord(ticker, LocalDate.of(2024, 1, 1), 1, 1, 1, 101, 1));
        priceBarRepository.upsert(new PriceBarRecord(ticker, LocalDate.of(2024, 1, 2), 1, 1, 1, 102, 1));

        List<PriceBarRecord> bars = priceBarRepository.findByTicker(ticker);

        assertThat(bars).extracting(PriceBarRecord::tradeDate)
                .containsExactly(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3));
    }

    @Test
    void findByTickerReturnsEmptyListForUnknownTicker() {
        String unknownTicker = newTestTicker();

        assertThat(priceBarRepository.findByTicker(unknownTicker)).isEmpty();
        assertThat(sentimentScoreRepository.findByTicker(unknownTicker)).isEmpty();
    }

    private static String newTestTicker() {
        return "TEST" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
