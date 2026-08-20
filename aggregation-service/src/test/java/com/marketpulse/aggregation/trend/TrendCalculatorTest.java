package com.marketpulse.aggregation.trend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class TrendCalculatorTest {

    @Test
    void computesPercentChangeFromTwoMostRecentBars() {
        TreeMap<LocalDate, PriceBarRecord> history = new TreeMap<>();
        history.put(LocalDate.of(2024, 1, 1), bar(LocalDate.of(2024, 1, 1), 100.0));
        history.put(LocalDate.of(2024, 1, 2), bar(LocalDate.of(2024, 1, 2), 110.0));

        TrendSummary summary = TrendCalculator.compute("AAPL", history, List.of());

        assertThat(summary.latestClose()).isEqualTo(110.0);
        assertThat(summary.percentChange()).isPresent();
        assertThat(summary.percentChange().get()).isEqualTo(10.0);
    }

    @Test
    void singleBarHasNoPercentChange() {
        TreeMap<LocalDate, PriceBarRecord> history = new TreeMap<>();
        history.put(LocalDate.of(2024, 1, 1), bar(LocalDate.of(2024, 1, 1), 100.0));

        TrendSummary summary = TrendCalculator.compute("AAPL", history, List.of());

        assertThat(summary.percentChange()).isEmpty();
        assertThat(summary.latestClose()).isEqualTo(100.0);
    }

    @Test
    void emptyPriceHistoryThrowsRatherThanFabricatingASummary() {
        TreeMap<LocalDate, PriceBarRecord> history = new TreeMap<>();

        assertThatThrownBy(() -> TrendCalculator.compute("AAPL", history, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void averagesSentimentAcrossAllRecords() {
        TreeMap<LocalDate, PriceBarRecord> history = new TreeMap<>();
        history.put(LocalDate.of(2024, 1, 1), bar(LocalDate.of(2024, 1, 1), 100.0));
        List<SentimentRecord> sentiments = List.of(
                sentiment("a", "positive", 0.5),
                sentiment("b", "negative", -0.3));

        TrendSummary summary = TrendCalculator.compute("AAPL", history, sentiments);

        assertThat(summary.averageSentiment()).isPresent();
        assertThat(summary.averageSentiment().get()).isEqualTo(0.1, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(summary.sentimentLabelCounts()).containsEntry("positive", 1L).containsEntry("negative", 1L);
    }

    @Test
    void noSentimentRecordsMeansAverageSentimentIsAbsent() {
        TreeMap<LocalDate, PriceBarRecord> history = new TreeMap<>();
        history.put(LocalDate.of(2024, 1, 1), bar(LocalDate.of(2024, 1, 1), 100.0));

        TrendSummary summary = TrendCalculator.compute("AAPL", history, List.of());

        assertThat(summary.averageSentiment()).isEmpty();
        assertThat(summary.sentimentLabelCounts()).isEmpty();
    }

    private static PriceBarRecord bar(LocalDate date, double close) {
        return new PriceBarRecord("AAPL", date, close, close, close, close, 1000L);
    }

    private static SentimentRecord sentiment(String articleUuid, String label, double score) {
        return new SentimentRecord("AAPL", articleUuid, label, score, Instant.parse("2024-01-01T00:00:00Z"));
    }
}
