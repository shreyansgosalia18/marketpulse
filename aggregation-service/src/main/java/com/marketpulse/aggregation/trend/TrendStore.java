package com.marketpulse.aggregation.trend;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.marketpulse.aggregation.persistence.PriceBarRepository;
import com.marketpulse.aggregation.persistence.SentimentScoreRepository;

/**
 * Durable store of price/sentiment history per ticker, backed by
 * PostgreSQL (see docs/user-stories/postgres-persistence-layer.md), with a
 * Redis-backed cache in front of computed trend summaries (see
 * docs/user-stories/redis-caching-layer.md). Recording is idempotent by
 * construction - price bars are keyed by (ticker, trade date) and
 * sentiment by (ticker, article UUID) via each repository's upsert, so
 * reprocessing the same Kafka message (at-least-once delivery) overwrites
 * with the same value rather than duplicating or double-counting it.
 * Recording also evicts that ticker's cache entry, so a cache hit is never
 * older than the most recent write.
 *
 * <p>Unbounded for this slice - no eviction or retention policy for the
 * underlying Postgres data (the cache itself does expire - see
 * TrendSummaryCache).
 */
@Service
public class TrendStore {

    private final PriceBarRepository priceBarRepository;
    private final SentimentScoreRepository sentimentScoreRepository;
    private final TrendSummaryCache cache;

    public TrendStore(
            PriceBarRepository priceBarRepository,
            SentimentScoreRepository sentimentScoreRepository,
            TrendSummaryCache cache) {
        this.priceBarRepository = priceBarRepository;
        this.sentimentScoreRepository = sentimentScoreRepository;
        this.cache = cache;
    }

    public void recordPriceBar(PriceBarRecord bar) {
        priceBarRepository.upsert(bar);
        cache.evict(bar.ticker());
    }

    public void recordSentiment(SentimentRecord record) {
        sentimentScoreRepository.upsert(record);
        cache.evict(record.ticker());
    }

    public Optional<TrendSummary> getTrendSummary(String ticker) {
        Optional<TrendSummary> cached = cache.get(ticker);
        if (cached.isPresent()) {
            return cached;
        }

        List<PriceBarRecord> bars = priceBarRepository.findByTicker(ticker);
        if (bars.isEmpty()) {
            return Optional.empty();
        }
        TreeMap<LocalDate, PriceBarRecord> priceHistory = new TreeMap<>();
        for (PriceBarRecord bar : bars) {
            priceHistory.put(bar.tradeDate(), bar);
        }
        List<SentimentRecord> sentimentRecords = sentimentScoreRepository.findByTicker(ticker);
        TrendSummary summary = TrendCalculator.compute(ticker, priceHistory, sentimentRecords);
        cache.put(ticker, summary);
        return Optional.of(summary);
    }

    /** Raw price history for a ticker, ordered by trade date - not cached (see the REST API story's scope decisions). */
    public List<PriceBarRecord> getPriceHistory(String ticker) {
        return priceBarRepository.findByTicker(ticker);
    }
}
