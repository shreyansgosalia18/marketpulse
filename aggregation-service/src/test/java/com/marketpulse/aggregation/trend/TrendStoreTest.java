package com.marketpulse.aggregation.trend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class TrendStoreTest {

    @Test
    void getTrendSummaryReturnsEmptyForUnknownTicker() {
        TrendStore store = new TrendStore();

        Optional<TrendSummary> summary = store.getTrendSummary("UNKNOWN");

        assertThat(summary).isEmpty();
    }

    @Test
    void recordsPriceBarByTradeDate() {
        TrendStore store = new TrendStore();

        store.recordPriceBar(bar(LocalDate.of(2024, 1, 1), 100.0));
        store.recordPriceBar(bar(LocalDate.of(2024, 1, 2), 105.0));

        TrendSummary summary = store.getTrendSummary("AAPL").orElseThrow();
        assertThat(summary.latestClose()).isEqualTo(105.0);
        assertThat(summary.percentChange()).isPresent();
    }

    @Test
    void recordingSamePriceBarTwiceIsIdempotent() {
        TrendStore store = new TrendStore();
        store.recordPriceBar(bar(LocalDate.of(2024, 1, 1), 100.0));
        store.recordPriceBar(bar(LocalDate.of(2024, 1, 2), 105.0));

        // Redelivery of the same message - same date, same values.
        store.recordPriceBar(bar(LocalDate.of(2024, 1, 2), 105.0));

        TrendSummary summary = store.getTrendSummary("AAPL").orElseThrow();
        assertThat(summary.latestClose()).isEqualTo(105.0);
        assertThat(summary.percentChange().get()).isEqualTo(5.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void recordsSentimentByArticleUuid() {
        TrendStore store = new TrendStore();
        store.recordPriceBar(bar(LocalDate.of(2024, 1, 1), 100.0));
        store.recordSentiment(sentiment("uuid-1", 0.5));

        TrendSummary summary = store.getTrendSummary("AAPL").orElseThrow();
        assertThat(summary.averageSentiment()).isPresent();
        assertThat(summary.averageSentiment().get()).isEqualTo(0.5);
    }

    @Test
    void recordingSameSentimentTwiceIsIdempotent() {
        TrendStore store = new TrendStore();
        store.recordPriceBar(bar(LocalDate.of(2024, 1, 1), 100.0));
        store.recordSentiment(sentiment("uuid-1", 0.5));

        // Redelivery of the same article's sentiment - must not double-count
        // toward the average.
        store.recordSentiment(sentiment("uuid-1", 0.5));

        TrendSummary summary = store.getTrendSummary("AAPL").orElseThrow();
        assertThat(summary.averageSentiment().get()).isEqualTo(0.5);
    }

    private static PriceBarRecord bar(LocalDate date, double close) {
        return new PriceBarRecord("AAPL", date, close, close, close, close, 1000L);
    }

    private static SentimentRecord sentiment(String articleUuid, double score) {
        return new SentimentRecord("AAPL", articleUuid, "positive", score, Instant.parse("2024-01-01T00:00:00Z"));
    }
}
