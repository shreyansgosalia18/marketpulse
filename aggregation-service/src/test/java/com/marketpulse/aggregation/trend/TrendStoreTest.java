package com.marketpulse.aggregation.trend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
 * TrendStore - see PersistenceIntegrationTest for that. Live cache-hit and
 * eviction behavior against real Redis is CachingIntegrationTest's job.
 * These tests cover what's still TrendStore's own responsibility:
 * delegating to the right repository/cache, and correctly assembling
 * repository results into what TrendCalculator expects.
 */
class TrendStoreTest {

    @Test
    void getTrendSummaryReturnsEmptyForUnknownTicker() {
        PriceBarRepository priceBarRepository = mock(PriceBarRepository.class);
        SentimentScoreRepository sentimentScoreRepository = mock(SentimentScoreRepository.class);
        TrendSummaryCache cache = mock(TrendSummaryCache.class);
        when(cache.get("UNKNOWN")).thenReturn(Optional.empty());
        when(priceBarRepository.findByTicker("UNKNOWN")).thenReturn(List.of());
        TrendStore store = new TrendStore(priceBarRepository, sentimentScoreRepository, cache);

        Optional<TrendSummary> summary = store.getTrendSummary("UNKNOWN");

        assertThat(summary).isEmpty();
    }

    @Test
    void getTrendSummaryPopulatesCacheOnMiss() {
        PriceBarRepository priceBarRepository = mock(PriceBarRepository.class);
        SentimentScoreRepository sentimentScoreRepository = mock(SentimentScoreRepository.class);
        TrendSummaryCache cache = mock(TrendSummaryCache.class);
        when(cache.get("AAPL")).thenReturn(Optional.empty());
        when(priceBarRepository.findByTicker("AAPL")).thenReturn(List.of(
                bar(LocalDate.of(2024, 1, 1), 100.0),
                bar(LocalDate.of(2024, 1, 2), 105.0)));
        when(sentimentScoreRepository.findByTicker("AAPL")).thenReturn(List.of(sentiment("uuid-1", 0.5)));
        TrendStore store = new TrendStore(priceBarRepository, sentimentScoreRepository, cache);

        TrendSummary summary = store.getTrendSummary("AAPL").orElseThrow();

        assertThat(summary.latestClose()).isEqualTo(105.0);
        assertThat(summary.percentChange()).isPresent();
        assertThat(summary.averageSentiment()).contains(0.5);
        verify(cache).put(eq("AAPL"), eq(summary));
    }

    @Test
    void getTrendSummaryReturnsCachedValueWithoutQueryingRepositories() {
        PriceBarRepository priceBarRepository = mock(PriceBarRepository.class);
        SentimentScoreRepository sentimentScoreRepository = mock(SentimentScoreRepository.class);
        TrendSummaryCache cache = mock(TrendSummaryCache.class);
        TrendSummary cached = new TrendSummary("AAPL", 999.0, Optional.empty(), Optional.empty(), java.util.Map.of());
        when(cache.get("AAPL")).thenReturn(Optional.of(cached));
        TrendStore store = new TrendStore(priceBarRepository, sentimentScoreRepository, cache);

        Optional<TrendSummary> summary = store.getTrendSummary("AAPL");

        assertThat(summary).contains(cached);
        verifyNoInteractions(priceBarRepository, sentimentScoreRepository);
    }

    @Test
    void recordPriceBarDelegatesToRepositoryAndEvictsCache() {
        PriceBarRepository priceBarRepository = mock(PriceBarRepository.class);
        SentimentScoreRepository sentimentScoreRepository = mock(SentimentScoreRepository.class);
        TrendSummaryCache cache = mock(TrendSummaryCache.class);
        TrendStore store = new TrendStore(priceBarRepository, sentimentScoreRepository, cache);
        PriceBarRecord record = bar(LocalDate.of(2024, 1, 1), 100.0);

        store.recordPriceBar(record);

        verify(priceBarRepository).upsert(eq(record));
        verify(cache).evict("AAPL");
    }

    @Test
    void recordSentimentDelegatesToRepositoryAndEvictsCache() {
        PriceBarRepository priceBarRepository = mock(PriceBarRepository.class);
        SentimentScoreRepository sentimentScoreRepository = mock(SentimentScoreRepository.class);
        TrendSummaryCache cache = mock(TrendSummaryCache.class);
        TrendStore store = new TrendStore(priceBarRepository, sentimentScoreRepository, cache);
        SentimentRecord record = sentiment("uuid-1", 0.5);

        store.recordSentiment(record);

        verify(sentimentScoreRepository).upsert(eq(record));
        verify(cache).evict("AAPL");
    }

    private static PriceBarRecord bar(LocalDate date, double close) {
        return new PriceBarRecord("AAPL", date, close, close, close, close, 1000L);
    }

    private static SentimentRecord sentiment(String articleUuid, double score) {
        return new SentimentRecord("AAPL", articleUuid, "positive", score, Instant.parse("2024-01-01T00:00:00Z"));
    }
}
