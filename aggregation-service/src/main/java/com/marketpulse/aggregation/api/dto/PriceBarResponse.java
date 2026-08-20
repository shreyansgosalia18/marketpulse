package com.marketpulse.aggregation.api.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

import com.marketpulse.aggregation.trend.PriceBarRecord;

@Schema(description = "One day of OHLCV price history")
public record PriceBarResponse(
        @Schema(description = "Ticker symbol", example = "AAPL") String ticker,
        @Schema(description = "Trading date") LocalDate tradeDate,
        @Schema(example = "230.10") double open,
        @Schema(example = "234.90") double high,
        @Schema(example = "229.50") double low,
        @Schema(example = "233.45") double close,
        @Schema(example = "52341000") long volume) {

    public static PriceBarResponse from(PriceBarRecord bar) {
        return new PriceBarResponse(
                bar.ticker(), bar.tradeDate(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume());
    }
}
