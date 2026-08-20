package com.marketpulse.aggregation.api.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

import com.marketpulse.aggregation.trend.TrendSummary;

/**
 * API response shape for a trend summary - deliberately not TrendSummary
 * itself (which uses Optional, right for internal Java code but not an
 * idiomatic JSON contract). See docs/user-stories/rest-api.md.
 */
@Schema(description = "Computed trend summary for a ticker")
public record TrendSummaryResponse(
        @Schema(description = "Ticker symbol", example = "AAPL") String ticker,
        @Schema(description = "Most recent close price", example = "233.45") double latestClose,
        @Schema(description = "Percent change from the previous close; absent if fewer than two price bars exist", example = "1.85")
                Double percentChange,
        @Schema(description = "Mean compound sentiment score across scored articles; absent if none exist", example = "0.42")
                Double averageSentiment,
        @Schema(description = "Count of scored articles by sentiment label") Map<String, Long> sentimentLabelCounts) {

    public static TrendSummaryResponse from(TrendSummary summary) {
        return new TrendSummaryResponse(
                summary.ticker(),
                summary.latestClose(),
                summary.percentChange().orElse(null),
                summary.averageSentiment().orElse(null),
                summary.sentimentLabelCounts());
    }
}
