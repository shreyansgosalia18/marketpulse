# Reference: Aggregation Service

**User story:** [aggregation-trend-computation](../user-stories/aggregation-trend-computation.md) · **Architecture:** [aggregation service internals](../architecture/aggregation-service.md)

Consumes `marketpulse.prices.raw` and `marketpulse.sentiment.raw`, and computes a per-ticker trend summary in memory.

> **Status:** consumer + computation implemented and tested, verified against a real broker. No REST API and no persistence yet — both are separate, later roadmap items. See [Known limitations](#known-limitations--not-yet-built).

## What a trend summary contains

- `latestClose` — most recent price bar's close.
- `percentChange` — percent change from the previous close, if at least two bars exist for the ticker; absent otherwise.
- `averageSentiment` — mean compound sentiment score across all scored articles for the ticker, if any exist; absent otherwise.
- `sentimentLabelCounts` — count of `positive`/`negative`/`neutral` articles.

## Running it

Requires Java 21 and the local Kafka stack running (see [local-dev.md](local-dev.md)); no system-wide Maven install needed — the project bundles the Maven wrapper.

```
cd aggregation-service
./mvnw spring-boot:run
```

The service starts consuming both topics immediately and keeps running (see [architecture](../architecture/aggregation-service.md#why-a-long-running-service-not-a-bounded-batch)). There's no REST/CLI to query a trend summary yet — that's this slice's explicit scope boundary; `TrendStore.getTrendSummary(ticker)` is currently only reachable from within the JVM (tests, or a future REST controller).

## Testing

```
cd aggregation-service
./mvnw test
```

19 tests:
- `TrendCalculatorTest`, `TrendStoreTest` — pure logic + idempotency, no Spring context, no Kafka.
- `PriceEventListenerTest`, `SentimentEventListenerTest` — listener parsing/isolation behavior, plain JUnit instantiation (constructor injection makes this possible without Spring context).
- `AggregationServiceIntegrationTest` — **live**, `@SpringBootTest` against the real local broker: publishes real price + sentiment messages with a plain `KafkaProducer` (deliberately not any of this project's own code) and asserts the computed `TrendSummary` reflects both. Skipped via `Assumptions.assumeTrue` if Kafka isn't reachable, so the rest of the suite stays green without Docker.

## Known limitations / not yet built

- **No REST API** — a separate, later roadmap item.
- **No persistence** — in-memory only. Everything is lost on restart; unbounded growth over a long-running process isn't addressed. Both are meant to be solved by the Postgres persistence-layer roadmap item, which should replace `TrendStore`'s storage without needing to change `TrendCalculator`.
- **No windowing** — every price bar and every sentiment score ever consumed is kept forever (in memory) for a ticker; there's no "last N days" retention policy yet.
- **Trend computation is intentionally simple** — two-bar price direction plus a flat sentiment average, not a validated price/sentiment correlation model. See the user story's scope decisions.

## Assumptions made

- **Spring Boot 4.1.0, Java 21**, scaffolded via `start.spring.io`'s `starter.zip` API rather than a local Spring Boot CLI/IDE — no system-wide Maven was installed, so the generated project's bundled Maven wrapper (`./mvnw`) is what makes this buildable at all in this environment. The Kafka dependency's Spring Initializr id is `kafka`, not `spring-kafka` (`kafka` resolves to `spring-boot-starter-kafka` under this Boot version).
- **An explicit `ObjectMapper` `@Bean` was required** (`config/JacksonConfig.java`). Without `spring-boot-starter-web`/`-json` on the classpath, Spring Boot's Jackson auto-configuration doesn't provide one, even with `jackson-databind` present directly — the app failed to start with `NoSuchBeanDefinitionException` for `ObjectMapper` until this was added explicitly. Chose to define it explicitly rather than pull in a web starter this service doesn't otherwise need yet.
- Same `127.0.0.1` (not `localhost`) bootstrap-server convention as the Python components — see [event-stream.md](event-stream.md) for why; the underlying cause (this machine resolving `localhost` to IPv6 first) applies to any Kafka client on this machine, not just the Python ones.
- The live integration test uses Spring Kafka's own `ContainerTestUtils.waitForAssignment(...)` (rather than a hand-rolled polling loop, like the Python integration tests used) to avoid the same class of rebalance-timing bug found in the sentiment pipeline's tests — publishing before a fresh consumer group's rebalance completes means the message can be missed under `"latest"` offset reset.
