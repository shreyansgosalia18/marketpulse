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
 * PostgreSQL (see docs/user-stories/postgres-persistence-layer.md).
 * Recording is idempotent by construction - price bars are keyed by
 * (ticker, trade date) and sentiment by (ticker, article UUID) via each
 * repository's upsert, so reprocessing the same Kafka message (at-least-
 * once delivery) overwrites with the same value rather than duplicating
 * or double-counting it.
 *
 * <p>Unbounded for this slice - no eviction or retention policy.
 */
@Service
public class TrendStore {

    private final PriceBarRepository priceBarRepository;
    private final SentimentScoreRepository sentimentScoreRepository;

    public TrendStore(PriceBarRepository priceBarRepository, SentimentScoreRepository sentimentScoreRepository) {
        this.priceBarRepository = priceBarRepository;
        this.sentimentScoreRepository = sentimentScoreRepository;
    }

    public void recordPriceBar(PriceBarRecord bar) {
        priceBarRepository.upsert(bar);
    }

    public void recordSentiment(SentimentRecord record) {
        sentimentScoreRepository.upsert(record);
    }

    public Optional<TrendSummary> getTrendSummary(String ticker) {
        List<PriceBarRecord> bars = priceBarRepository.findByTicker(ticker);
        if (bars.isEmpty()) {
            return Optional.empty();
        }
        TreeMap<LocalDate, PriceBarRecord> priceHistory = new TreeMap<>();
        for (PriceBarRecord bar : bars) {
            priceHistory.put(bar.tradeDate(), bar);
        }
        List<SentimentRecord> sentimentRecords = sentimentScoreRepository.findByTicker(ticker);
        return Optional.of(TrendCalculator.compute(ticker, priceHistory, sentimentRecords));
    }
}
