package com.marketpulse.aggregation.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketpulse.aggregation.kafka.dto.SentimentEventMessage;
import com.marketpulse.aggregation.trend.SentimentRecord;
import com.marketpulse.aggregation.trend.TrendStore;

/**
 * Thin consumer: parse, delegate to TrendStore. A malformed message is
 * logged and skipped, not thrown - one bad message must not stop the
 * listener from processing the rest of the topic.
 */
@Component
public class SentimentEventListener {

    private static final Logger log = LoggerFactory.getLogger(SentimentEventListener.class);

    private final TrendStore trendStore;
    private final ObjectMapper objectMapper;

    public SentimentEventListener(TrendStore trendStore, ObjectMapper objectMapper) {
        this.trendStore = trendStore;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "marketpulse.sentiment.raw")
    public void onMessage(String payload) {
        try {
            trendStore.recordSentiment(parse(payload));
        } catch (Exception exception) {
            log.warn("Skipping malformed sentiment event: {}", payload, exception);
        }
    }

    SentimentRecord parse(String payload) throws Exception {
        SentimentEventMessage message = objectMapper.readValue(payload, SentimentEventMessage.class);
        if (message.ticker() == null || message.articleUuid() == null || message.sentiment() == null) {
            throw new IllegalArgumentException("sentiment event missing required field(s)");
        }
        return new SentimentRecord(
                message.ticker(),
                message.articleUuid(),
                message.sentiment(),
                message.compoundScore(),
                message.scoredAt());
    }
}
