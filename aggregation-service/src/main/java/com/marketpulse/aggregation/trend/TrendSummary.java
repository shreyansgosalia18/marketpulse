package com.marketpulse.aggregation.trend;

import java.util.Map;
import java.util.Optional;

/**
 * A computed trend summary for one ticker: latest price plus whatever
 * sentiment signal is available. percentChange is absent when fewer than
 * two price bars exist; averageSentiment is absent when no sentiment has
 * been scored yet for this ticker.
 */
public record TrendSummary(
        String ticker,
        double latestClose,
        Optional<Double> percentChange,
        Optional<Double> averageSentiment,
        Map<String, Long> sentimentLabelCounts) {
}
