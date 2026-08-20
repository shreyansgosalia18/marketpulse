package com.marketpulse.aggregation.trend;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * In-memory store of price/sentiment history per ticker, and the trend
 * summaries computed from it. Recording is idempotent by construction:
 * price bars are keyed by trade date and sentiment by article UUID, so
 * reprocessing the same Kafka message (at-least-once delivery) overwrites
 * with the same value rather than duplicating or double-counting it.
 *
 * <p>Unbounded for this slice - no eviction or retention policy. Replacing
 * this with Postgres-backed storage is a separate, later roadmap item.
 */
@Service
public class TrendStore {

    private final Map<String, TreeMap<LocalDate, PriceBarRecord>> priceHistoryByTicker = new ConcurrentHashMap<>();
    private final Map<String, Map<String, SentimentRecord>> sentimentByTickerAndArticle = new ConcurrentHashMap<>();

    public void recordPriceBar(PriceBarRecord bar) {
        priceHistoryByTicker
                .computeIfAbsent(bar.ticker(), ticker -> new TreeMap<>())
                .put(bar.tradeDate(), bar);
    }

    public void recordSentiment(SentimentRecord record) {
        sentimentByTickerAndArticle
                .computeIfAbsent(record.ticker(), ticker -> new ConcurrentHashMap<>())
                .put(record.articleUuid(), record);
    }

    public Optional<TrendSummary> getTrendSummary(String ticker) {
        TreeMap<LocalDate, PriceBarRecord> priceHistory = priceHistoryByTicker.get(ticker);
        if (priceHistory == null || priceHistory.isEmpty()) {
            return Optional.empty();
        }
        Map<String, SentimentRecord> sentimentByArticle =
                sentimentByTickerAndArticle.getOrDefault(ticker, Map.of());
        return Optional.of(TrendCalculator.compute(ticker, priceHistory, sentimentByArticle.values()));
    }
}
