package com.marketpulse.aggregation.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketpulse.aggregation.kafka.dto.PriceEventMessage;
import com.marketpulse.aggregation.trend.PriceBarRecord;
import com.marketpulse.aggregation.trend.TrendStore;

/**
 * Thin consumer: parse, delegate to TrendStore. A malformed message is
 * logged and skipped, not thrown - one bad message must not stop the
 * listener from processing the rest of the topic.
 */
@Component
public class PriceEventListener {

    private static final Logger log = LoggerFactory.getLogger(PriceEventListener.class);

    private final TrendStore trendStore;
    private final ObjectMapper objectMapper;

    public PriceEventListener(TrendStore trendStore, ObjectMapper objectMapper) {
        this.trendStore = trendStore;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "marketpulse.prices.raw")
    public void onMessage(String payload) {
        try {
            trendStore.recordPriceBar(parse(payload));
        } catch (Exception exception) {
            log.warn("Skipping malformed price event: {}", payload, exception);
        }
    }

    PriceBarRecord parse(String payload) throws Exception {
        PriceEventMessage message = objectMapper.readValue(payload, PriceEventMessage.class);
        if (message.ticker() == null || message.tradeDate() == null) {
            throw new IllegalArgumentException("price event missing required field(s)");
        }
        return new PriceBarRecord(
                message.ticker(),
                message.tradeDate(),
                message.open(),
                message.high(),
                message.low(),
                message.close(),
                message.volume());
    }
}
