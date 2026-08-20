package com.marketpulse.aggregation.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marketpulse.aggregation.trend.PriceBarRecord;
import com.marketpulse.aggregation.trend.TrendStore;

class SentimentEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void validMessageIsRecordedInTrendStore() {
        TrendStore trendStore = new TrendStore();
        trendStore.recordPriceBar(new PriceBarRecord("AAPL", LocalDate.of(2024, 1, 1), 100, 100, 100, 100, 1000));
        SentimentEventListener listener = new SentimentEventListener(trendStore, objectMapper);

        listener.onMessage("""
                {"schema_version":1,"event_type":"sentiment_score","ticker":"AAPL","article_uuid":"abc-123",
                 "sentiment":"positive","compound_score":0.65,"scored_at":"2024-01-02T12:05:00Z"}
                """);

        assertThat(trendStore.getTrendSummary("AAPL").get().averageSentiment()).contains(0.65);
    }

    @Test
    void malformedMessageIsSkippedNotThrown() {
        TrendStore trendStore = new TrendStore();
        SentimentEventListener listener = new SentimentEventListener(trendStore, objectMapper);

        assertThatCode(() -> listener.onMessage("{\"not\":\"a valid sentiment event\"}")).doesNotThrowAnyException();
    }

    @Test
    void parseThrowsForMissingRequiredFields() {
        TrendStore trendStore = new TrendStore();
        SentimentEventListener listener = new SentimentEventListener(trendStore, objectMapper);

        assertThatThrownBy(() -> listener.parse("{\"compound_score\":0.5}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
