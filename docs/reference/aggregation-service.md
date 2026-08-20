# Reference: Aggregation Service

**User story:** [aggregation-trend-computation](../user-stories/aggregation-trend-computation.md) · **Architecture:** [aggregation service internals](../architecture/aggregation-service.md)

Consumes `marketpulse.prices.raw` and `marketpulse.sentiment.raw`, and computes a per-ticker trend summary from data durably stored in PostgreSQL.

> **Status:** consumer + computation + PostgreSQL persistence all implemented and tested, verified against real Kafka and Postgres — including an actual process kill + restart, confirming data survives. No REST API yet — see [Known limitations](#known-limitations--not-yet-built).

## What a trend summary contains

- `latestClose` — most recent price bar's close.
- `percentChange` — percent change from the previous close, if at least two bars exist for the ticker; absent otherwise.
- `averageSentiment` — mean compound sentiment score across all scored articles for the ticker, if any exist; absent otherwise.
- `sentimentLabelCounts` — count of `positive`/`negative`/`neutral` articles.

## Running it

Requires Java 21 and the local Kafka + PostgreSQL stack running (see [local-dev.md](local-dev.md)); no system-wide Maven install needed — the project bundles the Maven wrapper. Flyway runs the schema migration automatically on startup — no manual setup step.

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

22 tests:
- `TrendCalculatorTest` — pure computation logic, unchanged by the move to Postgres.
- `TrendStoreTest` — orchestration only (mocked repositories): does it delegate to the right repository, does it assemble query results into what `TrendCalculator` expects. Idempotency itself is no longer TrendStore's job — see `PersistenceIntegrationTest`.
- `PriceEventListenerTest`, `SentimentEventListenerTest` — listener parsing/isolation behavior, with a mocked `TrendStore` (plain JUnit instantiation — constructor injection makes this possible without Spring context).
- `PersistenceIntegrationTest` — **live**, `@SpringBootTest` against the real local Postgres: upsert-overwrites-by-key for both tables, ordering, empty-ticker behavior. Skipped if Postgres isn't reachable.
- `AggregationServiceIntegrationTest` — **live**, `@SpringBootTest` against the real local broker *and* Postgres: publishes real price + sentiment messages with a plain `KafkaProducer` (deliberately not any of this project's own code) and asserts the computed `TrendSummary` — now genuinely read back from Postgres — reflects both. Skipped if either Kafka or Postgres isn't reachable.

Both live `@SpringBootTest` classes clean up their own test rows in `@AfterEach` using a unique per-test ticker.

**Also manually verified beyond the automated suite**: ran the service live, published real price events via Kafka, confirmed the rows in Postgres via `psql`, killed the process outright (not just a graceful stop), confirmed the rows were still there with the process gone, restarted the service fresh, published one more event, and confirmed the new row appended to the pre-restart history — proving persistence across a real process death, not just something the test suite claims.

## Known limitations / not yet built

- **No REST API** — a separate, later roadmap item.
- **No windowing** — every price bar and every sentiment score ever consumed is kept forever in Postgres for a ticker; there's no "last N days" retention policy yet. Now a more real concern than when this was memory-bounded by process lifetime.
- **Trend computation is intentionally simple** — two-bar price direction plus a flat sentiment average, not a validated price/sentiment correlation model. See the user story's scope decisions.
- **First run of a persistent consumer group processes the entire topic backlog.** `spring.kafka.consumer.group-id=aggregation-service` with `auto-offset-reset=earliest` means the *first* time this service (under that group id) connects, it consumes and persists every message ever published to `marketpulse.prices.raw`/`marketpulse.sentiment.raw` — including old test messages from other components' test runs that were never previously consumed by a committed group. Expected Kafka behavior, not a bug, but worth knowing before being surprised by unfamiliar tickers showing up in Postgres after a first run.

## Assumptions made

- **Spring Boot 4.1.0, Java 21**, scaffolded via `start.spring.io`'s `starter.zip` API rather than a local Spring Boot CLI/IDE — no system-wide Maven was installed, so the generated project's bundled Maven wrapper (`./mvnw`) is what makes this buildable at all in this environment. Both the Kafka and Flyway dependencies have renamed Initializr ids under this Boot version: `kafka` resolves to `spring-boot-starter-kafka` (not `spring-kafka`), and `flyway` resolves to `spring-boot-starter-flyway` (not raw `flyway-core`). Checked via `curl -s https://start.spring.io/pom.xml?dependencies=...` rather than guessing, after being burned by exactly this once already on Kafka.
- **`spring-boot-starter-flyway` alone isn't enough for Postgres** — it must be paired with `flyway-database-postgresql` (Flyway splits per-database support into separate modules). Missing it doesn't fail loudly at the dependency level: the app boots, Flyway silently never creates the `flyway_schema_history` table or runs any migration, and every query then fails with `relation "price_bars" does not exist` — misleading, since it looks like an application-level bug rather than a missing dependency. Confirmed by checking Postgres directly (`\dt`, and querying `flyway_schema_history`) rather than trusting that a clean test run meant the schema existed. Adding `flyway-database-postgresql` alone still isn't sufficient either — without `spring-boot-starter-flyway`, Spring Boot's Flyway auto-configuration doesn't activate at all, so the two must be used together.
- **An explicit `ObjectMapper` `@Bean` was required** (`config/JacksonConfig.java`). Without `spring-boot-starter-web`/`-json` on the classpath, Spring Boot's Jackson auto-configuration doesn't provide one, even with `jackson-databind` present directly — the app failed to start with `NoSuchBeanDefinitionException` for `ObjectMapper` until this was added explicitly. Chose to define it explicitly rather than pull in a web starter this service doesn't otherwise need yet.
- Same `127.0.0.1` (not `localhost`) bootstrap-server convention as the Python components — see [event-stream.md](event-stream.md) for why; the underlying cause (this machine resolving `localhost` to IPv6 first) applies to any Kafka client on this machine, not just the Python ones.
- The live integration tests use Spring Kafka's own `ContainerTestUtils.waitForAssignment(...)` (rather than a hand-rolled polling loop, like the Python integration tests used) to avoid the same class of rebalance-timing bug found in the sentiment pipeline's tests — publishing before a fresh consumer group's rebalance completes means the message can be missed under `"latest"` offset reset.
- **Plain JDBC over JPA/Hibernate** for the persistence layer — see the [user story](../user-stories/postgres-persistence-layer.md)'s design decisions for why (native upsert fit, no relational complexity that would justify an ORM here).
