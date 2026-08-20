package com.marketpulse.aggregation.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marketpulse.aggregation.trend.TrendStore;

class PriceEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void validMessageIsRecordedInTrendStore() {
        TrendStore trendStore = new TrendStore();
        PriceEventListener listener = new PriceEventListener(trendStore, objectMapper);

        listener.onMessage("""
                {"schema_version":1,"event_type":"price_bar","ticker":"AAPL","trade_date":"2024-01-02",
                 "open":150.0,"high":151.5,"low":149.25,"close":150.75,"volume":1000000}
                """);

        assertThat(trendStore.getTrendSummary("AAPL")).isPresent();
        assertThat(trendStore.getTrendSummary("AAPL").get().latestClose()).isEqualTo(150.75);
    }

    @Test
    void malformedMessageIsSkippedNotThrown() {
        TrendStore trendStore = new TrendStore();
        PriceEventListener listener = new PriceEventListener(trendStore, objectMapper);

        assertThatCode(() -> listener.onMessage("{\"not\":\"a valid price event\"}")).doesNotThrowAnyException();
        assertThat(trendStore.getTrendSummary("AAPL")).isEmpty();
    }

    @Test
    void nonJsonMessageIsSkippedNotThrown() {
        TrendStore trendStore = new TrendStore();
        PriceEventListener listener = new PriceEventListener(trendStore, objectMapper);

        assertThatCode(() -> listener.onMessage("not json at all")).doesNotThrowAnyException();
    }

    @Test
    void parseThrowsForMissingRequiredFields() {
        TrendStore trendStore = new TrendStore();
        PriceEventListener listener = new PriceEventListener(trendStore, objectMapper);

        assertThatThrownBy(() -> listener.parse("{\"open\":150.0}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
