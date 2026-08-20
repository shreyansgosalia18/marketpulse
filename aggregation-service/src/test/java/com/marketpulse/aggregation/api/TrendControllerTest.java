package com.marketpulse.aggregation.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.marketpulse.aggregation.trend.PriceBarRecord;
import com.marketpulse.aggregation.trend.TrendStore;
import com.marketpulse.aggregation.trend.TrendSummary;

@WebMvcTest(TrendController.class)
class TrendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrendStore trendStore;

    @Test
    void getTrendSummaryReturnsOkWithSummaryBody() throws Exception {
        TrendSummary summary =
                new TrendSummary("AAPL", 233.45, Optional.of(1.85), Optional.of(0.42), Map.of("positive", 3L));
        when(trendStore.getTrendSummary("AAPL")).thenReturn(Optional.of(summary));

        mockMvc.perform(get("/api/v1/trends/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.latestClose").value(233.45))
                .andExpect(jsonPath("$.percentChange").value(1.85))
                .andExpect(jsonPath("$.averageSentiment").value(0.42));
    }

    @Test
    void getTrendSummaryReturns404ForUnknownTicker() throws Exception {
        when(trendStore.getTrendSummary("UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/trends/UNKNOWN")).andExpect(status().isNotFound());
    }

    @Test
    void getPriceHistoryReturnsOkWithBars() throws Exception {
        List<PriceBarRecord> bars = List.of(
                new PriceBarRecord("AAPL", LocalDate.of(2024, 1, 1), 100, 101, 99, 100.5, 1000),
                new PriceBarRecord("AAPL", LocalDate.of(2024, 1, 2), 100.5, 102, 100, 101.5, 1200));
        when(trendStore.getPriceHistory("AAPL")).thenReturn(bars);

        mockMvc.perform(get("/api/v1/trends/AAPL/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tradeDate").value("2024-01-01"))
                .andExpect(jsonPath("$[1].close").value(101.5));
    }

    @Test
    void getPriceHistoryReturns404ForUnknownTicker() throws Exception {
        when(trendStore.getPriceHistory("UNKNOWN")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/trends/UNKNOWN/history")).andExpect(status().isNotFound());
    }
}
