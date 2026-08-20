package com.marketpulse.aggregation.kafka.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Maps the marketpulse.sentiment.raw JSON schema - see docs/reference/sentiment-pipeline.md. */
public record SentimentEventMessage(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("event_type") String eventType,
        String ticker,
        @JsonProperty("article_uuid") String articleUuid,
        String sentiment,
        @JsonProperty("compound_score") double compoundScore,
        @JsonProperty("scored_at") Instant scoredAt) {
}
