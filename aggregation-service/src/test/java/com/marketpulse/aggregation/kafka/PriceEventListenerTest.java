package com.marketpulse.aggregation.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marketpulse.aggregation.trend.PriceBarRecord;
import com.marketpulse.aggregation.trend.TrendStore;

class PriceEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void validMessageIsRecordedInTrendStore() {
        TrendStore trendStore = mock(TrendStore.class);
        PriceEventListener listener = new PriceEventListener(trendStore, objectMapper);

        listener.onMessage("""
                {"schema_version":1,"event_type":"price_bar","ticker":"AAPL","trade_date":"2024-01-02",
                 "open":150.0,"high":151.5,"low":149.25,"close":150.75,"volume":1000000}
                """);

        verify(trendStore).recordPriceBar(new PriceBarRecord(
                "AAPL", LocalDate.of(2024, 1, 2), 150.0, 151.5, 149.25, 150.75, 1000000));
    }

    @Test
    void malformedMessageIsSkippedNotThrown() {
        TrendStore trendStore = mock(TrendStore.class);
        PriceEventListener listener = new PriceEventListener(trendStore, objectMapper);

        assertThatCode(() -> listener.onMessage("{\"not\":\"a valid price event\"}")).doesNotThrowAnyException();
        verify(trendStore, never()).recordPriceBar(any());
    }

    @Test
    void nonJsonMessageIsSkippedNotThrown() {
        TrendStore trendStore = mock(TrendStore.class);
        PriceEventListener listener = new PriceEventListener(trendStore, objectMapper);

        assertThatCode(() -> listener.onMessage("not json at all")).doesNotThrowAnyException();
    }

    @Test
    void parseThrowsForMissingRequiredFields() {
        TrendStore trendStore = mock(TrendStore.class);
        PriceEventListener listener = new PriceEventListener(trendStore, objectMapper);

        assertThatThrownBy(() -> listener.parse("{\"open\":150.0}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
