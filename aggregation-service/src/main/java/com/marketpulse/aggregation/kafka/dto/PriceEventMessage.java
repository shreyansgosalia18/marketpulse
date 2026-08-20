package com.marketpulse.aggregation.kafka.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Maps the marketpulse.prices.raw JSON schema - see docs/reference/event-stream.md. */
public record PriceEventMessage(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("event_type") String eventType,
        String ticker,
        @JsonProperty("trade_date") LocalDate tradeDate,
        double open,
        double high,
        double low,
        double close,
        long volume) {
}
