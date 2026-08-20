package com.marketpulse.aggregation.trend;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;

/**
 * Pure computation from stored history to a TrendSummary - no I/O, no
 * Spring dependency, independently testable. This is intentionally simple
 * (latest-two-bar price direction plus an average sentiment score), not a
 * claim of validated price/sentiment correlation - see the user story.
 */
public final class TrendCalculator {

    private TrendCalculator() {
    }

    public static TrendSummary compute(
            String ticker,
            SortedMap<LocalDate, PriceBarRecord> priceHistory,
            Collection<SentimentRecord> sentimentRecords) {
        if (priceHistory.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute a trend summary with no price history for " + ticker);
        }

        PriceBarRecord latest = priceHistory.get(priceHistory.lastKey());
        return new TrendSummary(
                ticker,
                latest.close(),
                computePercentChange(priceHistory),
                computeAverageSentiment(sentimentRecords),
                countLabels(sentimentRecords));
    }

    private static Optional<Double> computePercentChange(SortedMap<LocalDate, PriceBarRecord> priceHistory) {
        if (priceHistory.size() < 2) {
            return Optional.empty();
        }
        LocalDate[] dates = priceHistory.keySet().toArray(new LocalDate[0]);
        double previousClose = priceHistory.get(dates[dates.length - 2]).close();
        double latestClose = priceHistory.get(dates[dates.length - 1]).close();
        if (previousClose == 0.0) {
            return Optional.empty();
        }
        return Optional.of(((latestClose - previousClose) / previousClose) * 100.0);
    }

    private static Optional<Double> computeAverageSentiment(Collection<SentimentRecord> sentimentRecords) {
        if (sentimentRecords.isEmpty()) {
            return Optional.empty();
        }
        double sum = sentimentRecords.stream().mapToDouble(SentimentRecord::compoundScore).sum();
        return Optional.of(sum / sentimentRecords.size());
    }

    private static Map<String, Long> countLabels(Collection<SentimentRecord> sentimentRecords) {
        Map<String, Long> counts = new HashMap<>();
        for (SentimentRecord record : sentimentRecords) {
            counts.merge(record.label(), 1L, Long::sum);
        }
        return counts;
    }
}
