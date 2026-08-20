package com.marketpulse.aggregation;

import java.net.InetSocketAddress;
import java.net.Socket;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AggregationServiceApplicationTests {

    @BeforeAll
    static void checkKafkaAndPostgresReachable() {
        // Context construction needs both a Kafka connection and a working
        // DataSource (see docs/user-stories/postgres-persistence-layer.md) -
        // skip with a clear reason rather than a cryptic context-load failure.
        Assumptions.assumeTrue(isReachable("127.0.0.1", 9092), "Kafka broker not reachable at 127.0.0.1:9092");
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

    @Test
    void contextLoads() {
    }

}
