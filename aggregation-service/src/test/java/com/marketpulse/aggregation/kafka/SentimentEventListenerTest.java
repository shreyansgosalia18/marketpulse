package com.marketpulse.aggregation.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marketpulse.aggregation.trend.SentimentRecord;
import com.marketpulse.aggregation.trend.TrendStore;

class SentimentEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void validMessageIsRecordedInTrendStore() {
        TrendStore trendStore = mock(TrendStore.class);
        SentimentEventListener listener = new SentimentEventListener(trendStore, objectMapper);

        listener.onMessage("""
                {"schema_version":1,"event_type":"sentiment_score","ticker":"AAPL","article_uuid":"abc-123",
                 "sentiment":"positive","compound_score":0.65,"scored_at":"2024-01-02T12:05:00Z"}
                """);

        verify(trendStore).recordSentiment(new SentimentRecord(
                "AAPL", "abc-123", "positive", 0.65, Instant.parse("2024-01-02T12:05:00Z")));
    }

    @Test
    void malformedMessageIsSkippedNotThrown() {
        TrendStore trendStore = mock(TrendStore.class);
        SentimentEventListener listener = new SentimentEventListener(trendStore, objectMapper);

        assertThatCode(() -> listener.onMessage("{\"not\":\"a valid sentiment event\"}")).doesNotThrowAnyException();
        verify(trendStore, never()).recordSentiment(any());
    }

    @Test
    void parseThrowsForMissingRequiredFields() {
        TrendStore trendStore = mock(TrendStore.class);
        SentimentEventListener listener = new SentimentEventListener(trendStore, objectMapper);

        assertThatThrownBy(() -> listener.parse("{\"compound_score\":0.5}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
