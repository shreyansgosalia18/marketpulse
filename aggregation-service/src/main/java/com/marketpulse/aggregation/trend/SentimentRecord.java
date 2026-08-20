package com.marketpulse.aggregation.trend;

import java.time.Instant;

/** One scored article's sentiment for a ticker, as stored in the trend store. */
public record SentimentRecord(
        String ticker,
        String articleUuid,
        String label,
        double compoundScore,
        Instant scoredAt) {
}
