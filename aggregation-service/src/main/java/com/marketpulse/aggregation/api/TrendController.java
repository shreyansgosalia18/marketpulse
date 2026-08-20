package com.marketpulse.aggregation.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.marketpulse.aggregation.api.dto.PriceBarResponse;
import com.marketpulse.aggregation.api.dto.TrendSummaryResponse;
import com.marketpulse.aggregation.trend.TrendStore;

/**
 * A ticker with no data is a 404 for both endpoints, not a 200 with an
 * empty/null body - there's no independent "known tickers" registry in
 * this system, so absence of data is absence of the resource. See
 * docs/user-stories/rest-api.md.
 */
@RestController
@Tag(name = "Trends", description = "Per-ticker trend summaries and price history")
public class TrendController {

    private final TrendStore trendStore;

    public TrendController(TrendStore trendStore) {
        this.trendStore = trendStore;
    }

    @GetMapping("/api/v1/trends/{ticker}")
    @Operation(summary = "Get the current trend summary for a ticker")
    @ApiResponse(responseCode = "200", description = "Trend summary found")
    @ApiResponse(responseCode = "404", description = "No data for this ticker")
    public ResponseEntity<TrendSummaryResponse> getTrendSummary(@PathVariable String ticker) {
        return trendStore.getTrendSummary(ticker)
                .map(TrendSummaryResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/trends/{ticker}/history")
    @Operation(summary = "Get daily price history for a ticker")
    @ApiResponse(responseCode = "200", description = "Price history found")
    @ApiResponse(responseCode = "404", description = "No data for this ticker")
    public ResponseEntity<List<PriceBarResponse>> getPriceHistory(@PathVariable String ticker) {
        List<PriceBarResponse> bars =
                trendStore.getPriceHistory(ticker).stream().map(PriceBarResponse::from).toList();
        if (bars.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bars);
    }
}
