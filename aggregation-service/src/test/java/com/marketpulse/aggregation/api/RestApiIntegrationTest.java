package com.marketpulse.aggregation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.marketpulse.aggregation.trend.PriceBarRecord;
import com.marketpulse.aggregation.trend.TrendStore;
import com.marketpulse.aggregation.trend.TrendSummaryCache;

/**
 * Live against the real local Postgres (TrendStore needs it to seed data;
 * Redis is a soft dependency with no reachability check needed - see
 * docs/reference/aggregation-service.md). Skipped if Postgres isn't
 * reachable. Seeds via TrendStore directly rather than Kafka - the
 * Kafka-to-Postgres path is already covered by
 * AggregationServiceIntegrationTest; this test's job is the HTTP contract.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RestApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TrendStore trendStore;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private TrendSummaryCache cache;

    private String ticker;

    @BeforeAll
    static void checkPostgresReachable() {
        Assumptions.assumeTrue(isReachable("127.0.0.1", 5432), "Postgres not reachable at 127.0.0.1:5432");
    }

    private static boolean isReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    @AfterEach
    void cleanUp() {
        if (ticker == null) {
            return;
        }
        cache.evict(ticker);
        jdbcTemplate.update("DELETE FROM price_bars WHERE ticker = :ticker", new MapSqlParameterSource("ticker", ticker));
        jdbcTemplate.update(
                "DELETE FROM sentiment_scores WHERE ticker = :ticker", new MapSqlParameterSource("ticker", ticker));
    }

    @Test
    void getTrendSummaryReturnsRealDataForATickerWithHistory() {
        ticker = newTestTicker();
        trendStore.recordPriceBar(new PriceBarRecord(ticker, LocalDate.of(2024, 1, 1), 100, 100, 100, 100, 1000));
        trendStore.recordPriceBar(new PriceBarRecord(ticker, LocalDate.of(2024, 1, 2), 100, 100, 100, 110, 1000));

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/trends/" + ticker, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"latestClose\":110.0").contains("\"percentChange\":10.0");
    }

    @Test
    void getTrendSummaryReturns404ForUnknownTicker() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/trends/" + newTestTicker(), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void getPriceHistoryReturnsRealDataForATickerWithHistory() {
        ticker = newTestTicker();
        trendStore.recordPriceBar(new PriceBarRecord(ticker, LocalDate.of(2024, 1, 1), 100, 100, 100, 100, 1000));

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/trends/" + ticker + "/history", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"tradeDate\":\"2024-01-01\"");
    }

    @Test
    void getPriceHistoryReturns404ForUnknownTicker() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/trends/" + newTestTicker() + "/history", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void swaggerUiIsReachable() {
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsIgnoringCase("swagger");
    }

    @Test
    void openApiDocumentDescribesBothEndpoints() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("/api/v1/trends/{ticker}").contains("/api/v1/trends/{ticker}/history");
    }

    private static String newTestTicker() {
        return "TEST" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
