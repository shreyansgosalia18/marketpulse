package com.marketpulse.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.marketpulse.aggregation.trend.TrendStore;
import com.marketpulse.aggregation.trend.TrendSummaryCache;

/**
 * Live integration test against the real local Kafka broker (see
 * docs/reference/local-dev.md). Skipped automatically if it isn't
 * reachable. Deliberately publishes with a plain KafkaProducer, not this
 * project's own listener code, matching the documented event-stream schema
 * only - proving this service depends on the Kafka contract, not on
 * scraper/sentiment-pipeline code.
 */
@SpringBootTest
class AggregationServiceIntegrationTest {

    @Autowired
    private TrendStore trendStore;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private TrendSummaryCache cache;

    private static KafkaProducer<String, String> producer;

    private String ticker;

    @DynamicPropertySource
    static void freshConsumerGroup(DynamicPropertyRegistry registry) {
        // Isolated group + "latest" so this test never reprocesses the
        // backlog of messages other components' tests have published to
        // these topics, and never pollutes the real "aggregation-service"
        // consumer group's committed offsets.
        registry.add("spring.kafka.consumer.group-id", () -> "test-" + UUID.randomUUID());
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "latest");
    }

    @BeforeAll
    static void checkKafkaAndPostgresReachableAndSetUpProducer() {
        // TrendStore now needs a working DataSource to construct at all
        // (see docs/user-stories/postgres-persistence-layer.md), so the
        // whole Spring context fails to start without Postgres too - check
        // both before letting @SpringBootTest attempt context creation.
        Assumptions.assumeTrue(isReachable("127.0.0.1", 9092), "Kafka broker not reachable at 127.0.0.1:9092");
        Assumptions.assumeTrue(isReachable("127.0.0.1", 5432), "Postgres not reachable at 127.0.0.1:5432");

        Properties props = new Properties();
        props.put("bootstrap.servers", "127.0.0.1:9092");
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        producer = new KafkaProducer<>(props);
    }

    @AfterAll
    static void closeProducer() {
        if (producer != null) {
            producer.close();
        }
    }

    @AfterEach
    void cleanUpTestData() {
        if (ticker == null) {
            return;
        }
        cache.evict(ticker);
        jdbcTemplate.update("DELETE FROM price_bars WHERE ticker = :ticker", new MapSqlParameterSource("ticker", ticker));
        jdbcTemplate.update(
                "DELETE FROM sentiment_scores WHERE ticker = :ticker", new MapSqlParameterSource("ticker", ticker));
    }

    private static boolean isReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    @Test
    void consumesRealPriceAndSentimentEventsAndComputesTrend() {
        // Wait for both listener containers to actually have their
        // partition assigned before publishing - otherwise a message
        // published right after context startup can arrive before the
        // consumer group's rebalance finishes and gets missed under
        // "latest" offset reset (the same class of bug found and fixed in
        // the sentiment pipeline's own integration test).
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 1);
        }

        ticker = "TEST" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String priceDay1 = """
                {"schema_version":1,"event_type":"price_bar","ticker":"%s","trade_date":"2024-01-01",
                 "open":100.0,"high":100.0,"low":100.0,"close":100.0,"volume":1000}
                """.formatted(ticker);
        String priceDay2 = """
                {"schema_version":1,"event_type":"price_bar","ticker":"%s","trade_date":"2024-01-02",
                 "open":110.0,"high":110.0,"low":110.0,"close":110.0,"volume":1000}
                """.formatted(ticker);
        String sentiment = """
                {"schema_version":1,"event_type":"sentiment_score","ticker":"%s","article_uuid":"%s",
                 "sentiment":"positive","compound_score":0.7,"scored_at":"2024-01-02T00:00:00Z"}
                """.formatted(ticker, UUID.randomUUID());

        producer.send(new ProducerRecord<>("marketpulse.prices.raw", ticker, priceDay1));
        producer.send(new ProducerRecord<>("marketpulse.prices.raw", ticker, priceDay2));
        producer.send(new ProducerRecord<>("marketpulse.sentiment.raw", ticker, sentiment));
        producer.flush();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var summary = trendStore.getTrendSummary(ticker);
            assertThat(summary).isPresent();
            assertThat(summary.get().latestClose()).isEqualTo(110.0);
            assertThat(summary.get().percentChange()).contains(10.0);
            assertThat(summary.get().averageSentiment()).contains(0.7);
        });
    }
}
