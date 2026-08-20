# Reference: Aggregation Service

**User story:** [aggregation-trend-computation](../user-stories/aggregation-trend-computation.md) · **Architecture:** [aggregation service internals](../architecture/aggregation-service.md)

Consumes `marketpulse.prices.raw` and `marketpulse.sentiment.raw`, computes a per-ticker trend summary from data durably stored in PostgreSQL, and caches the computed result in Redis.

> **Status:** consumer + computation + PostgreSQL persistence + Redis caching all implemented and tested, verified against real Kafka, Postgres, and Redis — including an actual process kill + restart (proving Postgres persistence) and an actual Redis outage (proving the cache degrades gracefully rather than breaking anything). No REST API yet — see [Known limitations](#known-limitations--not-yet-built).

## What a trend summary contains

- `latestClose` — most recent price bar's close.
- `percentChange` — percent change from the previous close, if at least two bars exist for the ticker; absent otherwise.
- `averageSentiment` — mean compound sentiment score across all scored articles for the ticker, if any exist; absent otherwise.
- `sentimentLabelCounts` — count of `positive`/`negative`/`neutral` articles.

## Running it

Requires Java 21 and the local Kafka + PostgreSQL + Redis stack running (see [local-dev.md](local-dev.md)) — though Redis is a soft dependency; the service still runs correctly (just without caching) if it's down. No system-wide Maven install needed — the project bundles the Maven wrapper. Flyway runs the schema migration automatically on startup — no manual setup step.

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

28 tests:
- `TrendCalculatorTest` — pure computation logic, unchanged by the move to Postgres or the addition of caching.
- `TrendStoreTest` — orchestration only (mocked repositories + mocked cache): cache hit short-circuits the repositories entirely, cache miss queries and populates, writes evict. Idempotency itself is Postgres's job (`PersistenceIntegrationTest`); cache correctness against real Redis is `CachingIntegrationTest`'s job.
- `TrendSummaryCacheTest` — mocked Redis, proving cache failures are swallowed (logged, not thrown) rather than breaking the caller.
- `PriceEventListenerTest`, `SentimentEventListenerTest` — listener parsing/isolation behavior, with a mocked `TrendStore` (plain JUnit instantiation — constructor injection makes this possible without Spring context).
- `PersistenceIntegrationTest` — **live**, `@SpringBootTest` against the real local Postgres: upsert-overwrites-by-key for both tables, ordering, empty-ticker behavior. Skipped if Postgres isn't reachable.
- `CachingIntegrationTest` — **live**, `@SpringBootTest` against real Redis (and Postgres, since `TrendStore` needs both): real serialize/deserialize round-trip including `Optional` fields; a value seeded directly into the cache (for a ticker with *no* matching Postgres rows) is what `TrendStore.getTrendSummary` returns, proving the cache is actually consulted; recording new data for a cached ticker evicts it. Skipped if either Redis or Postgres isn't reachable.
- `AggregationServiceIntegrationTest` — **live**, `@SpringBootTest` against the real local broker *and* Postgres: publishes real price + sentiment messages with a plain `KafkaProducer` (deliberately not any of this project's own code) and asserts the computed `TrendSummary` — genuinely read back from Postgres — reflects both. Skipped if either Kafka or Postgres isn't reachable. Deliberately has **no** Redis reachability check (see below).

All live `@SpringBootTest` classes clean up their own test rows (Postgres) and cache entries (Redis) in `@AfterEach` using a unique per-test ticker.

**Also manually verified beyond the automated suite:**
- **Postgres persistence**: ran the service live, published real price events via Kafka, confirmed the rows in Postgres via `psql`, killed the process outright (not just a graceful stop), confirmed the rows were still there with the process gone, restarted the service fresh, published one more event, and confirmed the new row appended to the pre-restart history.
- **Redis graceful degradation**: stopped the Redis container outright, then ran `AggregationServiceApplicationTests` and `AggregationServiceIntegrationTest` (neither has a Redis reachability guard, deliberately) — both still passed, with real `RedisConnectionFailureException`s visible in the logs at every cache read/write/eviction attempt, all caught and logged rather than propagated. Restarted Redis and confirmed the full suite passes normally again.

## Known limitations / not yet built

- **No REST API** — a separate, later roadmap item.
- **No windowing** — every price bar and every sentiment score ever consumed is kept forever in Postgres for a ticker; there's no "last N days" retention policy yet. Now a more real concern than when this was memory-bounded by process lifetime.
- **Trend computation is intentionally simple** — two-bar price direction plus a flat sentiment average, not a validated price/sentiment correlation model. See the user story's scope decisions.
- **No cache warming or hit-rate metrics** — entries populate lazily on first request; nothing observes how effective the cache actually is yet.
- **First run of a persistent consumer group processes the entire topic backlog.** `spring.kafka.consumer.group-id=aggregation-service` with `auto-offset-reset=earliest` means the *first* time this service (under that group id) connects, it consumes and persists every message ever published to `marketpulse.prices.raw`/`marketpulse.sentiment.raw` — including old test messages from other components' test runs that were never previously consumed by a committed group. Expected Kafka behavior, not a bug, but worth knowing before being surprised by unfamiliar tickers showing up in Postgres after a first run.

## Assumptions made

- **Spring Boot 4.1.0, Java 21**, scaffolded via `start.spring.io`'s `starter.zip` API rather than a local Spring Boot CLI/IDE — no system-wide Maven was installed, so the generated project's bundled Maven wrapper (`./mvnw`) is what makes this buildable at all in this environment. Both the Kafka and Flyway dependencies have renamed Initializr ids under this Boot version: `kafka` resolves to `spring-boot-starter-kafka` (not `spring-kafka`), and `flyway` resolves to `spring-boot-starter-flyway` (not raw `flyway-core`). Checked via `curl -s https://start.spring.io/pom.xml?dependencies=...` rather than guessing, after being burned by exactly this once already on Kafka.
- **`spring-boot-starter-flyway` alone isn't enough for Postgres** — it must be paired with `flyway-database-postgresql` (Flyway splits per-database support into separate modules). Missing it doesn't fail loudly at the dependency level: the app boots, Flyway silently never creates the `flyway_schema_history` table or runs any migration, and every query then fails with `relation "price_bars" does not exist` — misleading, since it looks like an application-level bug rather than a missing dependency. Confirmed by checking Postgres directly (`\dt`, and querying `flyway_schema_history`) rather than trusting that a clean test run meant the schema existed. Adding `flyway-database-postgresql` alone still isn't sufficient either — without `spring-boot-starter-flyway`, Spring Boot's Flyway auto-configuration doesn't activate at all, so the two must be used together.
- **An explicit `ObjectMapper` `@Bean` was required** (`config/JacksonConfig.java`). Without `spring-boot-starter-web`/`-json` on the classpath, Spring Boot's Jackson auto-configuration doesn't provide one, even with `jackson-databind` present directly — the app failed to start with `NoSuchBeanDefinitionException` for `ObjectMapper` until this was added explicitly. Chose to define it explicitly rather than pull in a web starter this service doesn't otherwise need yet.
- Same `127.0.0.1` (not `localhost`) bootstrap-server convention as the Python components — see [event-stream.md](event-stream.md) for why; the underlying cause (this machine resolving `localhost` to IPv6 first) applies to any Kafka client on this machine, not just the Python ones.
- The live integration tests use Spring Kafka's own `ContainerTestUtils.waitForAssignment(...)` (rather than a hand-rolled polling loop, like the Python integration tests used) to avoid the same class of rebalance-timing bug found in the sentiment pipeline's tests — publishing before a fresh consumer group's rebalance completes means the message can be missed under `"latest"` offset reset.
- **Plain JDBC over JPA/Hibernate** for the persistence layer — see the [postgres-persistence-layer](../user-stories/postgres-persistence-layer.md) story's design decisions for why (native upsert fit, no relational complexity that would justify an ORM here).
- **`StringRedisTemplate` + Jackson over Spring Data Redis repositories** — same reasoning: this is "get/put/evict one JSON blob by key," not a query need. `jackson-datatype-jdk8` was added so `TrendSummary`'s `Optional<Double>` fields serialize directly rather than needing a parallel cache-only DTO with nullable fields.
- **No reachability guard added for `AggregationServiceApplicationTests`/`AggregationServiceIntegrationTest`** (unlike Kafka and Postgres, which do have one) — deliberately. Spring's Lettuce Redis client connects lazily, so context startup doesn't need Redis at all, and `TrendSummaryCache`'s defensive design means functional correctness doesn't either. Skipping the guard here is itself a live proof that graceful degradation works, not just an oversight — confirmed by literally stopping Redis and watching both tests still pass.
- Redis's Spring Initializr id (`data-redis`) resolves to `spring-boot-starter-data-redis` under this Boot version — matched the already-known `-data-jpa`-style convention, no surprise this time (unlike Kafka and Flyway).
