package com.marketpulse.aggregation.trend;

import java.time.LocalDate;

/** One day of OHLCV price history for a ticker, as stored in the trend store. */
public record PriceBarRecord(
        String ticker,
        LocalDate tradeDate,
        double open,
        double high,
        double low,
        double close,
        long volume) {
}
