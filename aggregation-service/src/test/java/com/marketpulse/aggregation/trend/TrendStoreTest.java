package com.marketpulse.aggregation.trend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.marketpulse.aggregation.persistence.PriceBarRepository;
import com.marketpulse.aggregation.persistence.SentimentScoreRepository;

/**
 * Idempotency itself is now enforced by Postgres's ON CONFLICT upsert, not
 * TrendStore - see PersistenceIntegrationTest for that. These tests cover
 * what's still TrendStore's own responsibility: delegating to the right
 * repository, and correctly assembling repository results into what
 * TrendCalculator expects.
 */
class TrendStoreTest {

    @Test
    void getTrendSummaryReturnsEmptyForUnknownTicker() {
        PriceBarRepository priceBarRepository = mock(PriceBarRepository.class);
        SentimentScoreRepository sentimentScoreRepository = mock(SentimentScoreRepository.class);
        when(priceBarRepository.findByTicker("UNKNOWN")).thenReturn(List.of());
        TrendStore store = new TrendStore(priceBarRepository, sentimentScoreRepository);

        Optional<TrendSummary> summary = store.getTrendSummary("UNKNOWN");

        assertThat(summary).isEmpty();
    }

    @Test
    void getTrendSummaryComputesFromRepositoryData() {
        PriceBarRepository priceBarRepository = mock(PriceBarRepository.class);
        SentimentScoreRepository sentimentScoreRepository = mock(SentimentScoreRepository.class);
        when(priceBarRepository.findByTicker("AAPL")).thenReturn(List.of(
                bar(LocalDate.of(2024, 1, 1), 100.0),
                bar(LocalDate.of(2024, 1, 2), 105.0)));
        when(sentimentScoreRepository.findByTicker("AAPL")).thenReturn(List.of(sentiment("uuid-1", 0.5)));
        TrendStore store = new TrendStore(priceBarRepository, sentimentScoreRepository);

        TrendSummary summary = store.getTrendSummary("AAPL").orElseThrow();

        assertThat(summary.latestClose()).isEqualTo(105.0);
        assertThat(summary.percentChange()).isPresent();
        assertThat(summary.averageSentiment()).contains(0.5);
    }

    @Test
    void recordPriceBarDelegatesToRepository() {
        PriceBarRepository priceBarRepository = mock(PriceBarRepository.class);
        SentimentScoreRepository sentimentScoreRepository = mock(SentimentScoreRepository.class);
        TrendStore store = new TrendStore(priceBarRepository, sentimentScoreRepository);
        PriceBarRecord record = bar(LocalDate.of(2024, 1, 1), 100.0);

        store.recordPriceBar(record);

        verify(priceBarRepository).upsert(eq(record));
    }

    @Test
    void recordSentimentDelegatesToRepository() {
        PriceBarRepository priceBarRepository = mock(PriceBarRepository.class);
        SentimentScoreRepository sentimentScoreRepository = mock(SentimentScoreRepository.class);
        TrendStore store = new TrendStore(priceBarRepository, sentimentScoreRepository);
        SentimentRecord record = sentiment("uuid-1", 0.5);

        store.recordSentiment(record);

        verify(sentimentScoreRepository).upsert(eq(record));
    }

    private static PriceBarRecord bar(LocalDate date, double close) {
        return new PriceBarRecord("AAPL", date, close, close, close, close, 1000L);
    }

    private static SentimentRecord sentiment(String articleUuid, double score) {
        return new SentimentRecord("AAPL", articleUuid, "positive", score, Instant.parse("2024-01-01T00:00:00Z"));
    }
}
