# Reference: Aggregation Service

**User story:** [aggregation-trend-computation](../user-stories/aggregation-trend-computation.md) · **Architecture:** [aggregation service internals](../architecture/aggregation-service.md)

Consumes `marketpulse.prices.raw` and `marketpulse.sentiment.raw`, computes a per-ticker trend summary from data durably stored in PostgreSQL, caches the computed result in Redis, and exposes both the summary and the raw price history over a REST API documented with Swagger/OpenAPI.

> **Status:** consumer + computation + PostgreSQL persistence + Redis caching + REST API all implemented and tested, verified against real Kafka, Postgres, and Redis — including an actual process kill + restart (proving Postgres persistence), an actual Redis outage (proving the cache degrades gracefully), and live `curl`/Swagger requests against a running instance.

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

The service starts consuming both topics immediately and keeps running (see [architecture](../architecture/aggregation-service.md#why-a-long-running-service-not-a-bounded-batch)). Once it's up:

| What | URL |
|---|---|
| Trend summary for a ticker | `GET http://localhost:8080/api/v1/trends/{ticker}` |
| Price history for a ticker | `GET http://localhost:8080/api/v1/trends/{ticker}/history` |
| Interactive Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| Raw OpenAPI 3 JSON | `http://localhost:8080/v3/api-docs` |

Both endpoints return `404` for a ticker with no data. There's a chicken-and-egg step for trying this fresh: nothing is queryable until the scraper → Kafka → this service pipeline has actually ingested a ticker — see [docs/reference/event-stream.md](event-stream.md) for how to publish data manually if you just want to poke the API without running the whole pipeline.

## Testing

```
cd aggregation-service
./mvnw test
```

38 tests:
- `TrendCalculatorTest` — pure computation logic, unchanged by the move to Postgres, the addition of caching, or the REST API.
- `TrendStoreTest` — orchestration only (mocked repositories + mocked cache): cache hit short-circuits the repositories entirely, cache miss queries and populates, writes evict. Idempotency itself is Postgres's job (`PersistenceIntegrationTest`); cache correctness against real Redis is `CachingIntegrationTest`'s job.
- `TrendSummaryCacheTest` — mocked Redis, proving cache failures are swallowed (logged, not thrown) rather than breaking the caller.
- `PriceEventListenerTest`, `SentimentEventListenerTest` — listener parsing/isolation behavior, with a mocked `TrendStore` (plain JUnit instantiation — constructor injection makes this possible without Spring context).
- `TrendControllerTest` — `@WebMvcTest` slice test with a mocked `TrendStore`: correct status codes and response bodies for both endpoints, both the happy path and the 404 path.
- `PersistenceIntegrationTest` — **live**, `@SpringBootTest` against the real local Postgres: upsert-overwrites-by-key for both tables, ordering, empty-ticker behavior. Skipped if Postgres isn't reachable.
- `CachingIntegrationTest` — **live**, `@SpringBootTest` against real Redis (and Postgres, since `TrendStore` needs both): real serialize/deserialize round-trip including `Optional` fields; a value seeded directly into the cache (for a ticker with *no* matching Postgres rows) is what `TrendStore.getTrendSummary` returns, proving the cache is actually consulted; recording new data for a cached ticker evicts it. Skipped if either Redis or Postgres isn't reachable.
- `AggregationServiceIntegrationTest` — **live**, `@SpringBootTest` against the real local broker *and* Postgres: publishes real price + sentiment messages with a plain `KafkaProducer` (deliberately not any of this project's own code) and asserts the computed `TrendSummary` — genuinely read back from Postgres — reflects both. Skipped if either Kafka or Postgres isn't reachable. Deliberately has **no** Redis reachability check (see below).
- `RestApiIntegrationTest` — **live**, `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a real `TestRestTemplate`: seeds data via `TrendStore` directly (the Kafka-to-Postgres path is already covered elsewhere; this test's job is the HTTP contract), then hits the real endpoints and asserts status codes and JSON bodies — including confirming `/swagger-ui/index.html` loads and `/v3/api-docs` describes both endpoints. Skipped if Postgres isn't reachable.

All live `@SpringBootTest` classes clean up their own test rows (Postgres) and cache entries (Redis) in `@AfterEach` using a unique per-test ticker.

**Also manually verified beyond the automated suite:**
- **Postgres persistence**: ran the service live, published real price events via Kafka, confirmed the rows in Postgres via `psql`, killed the process outright (not just a graceful stop), confirmed the rows were still there with the process gone, restarted the service fresh, published one more event, and confirmed the new row appended to the pre-restart history.
- **Redis graceful degradation**: stopped the Redis container outright, then ran `AggregationServiceApplicationTests` and `AggregationServiceIntegrationTest` (neither has a Redis reachability guard, deliberately) — both still passed, with real `RedisConnectionFailureException`s visible in the logs at every cache read/write/eviction attempt, all caught and logged rather than propagated. Restarted Redis and confirmed the full suite passes normally again.
- **REST API**: ran the service live, published real price events via Kafka, then `curl`ed both endpoints and got back real computed data; confirmed a `404` for a nonexistent ticker; confirmed `/swagger-ui/index.html` returns `200` and `/v3/api-docs` lists both endpoints with the configured title/description, not the generic springdoc default.

## Known limitations / not yet built

- **No windowing** — every price bar and every sentiment score ever consumed is kept forever in Postgres for a ticker; there's no "last N days" retention policy yet. Now a more real concern than when this was memory-bounded by process lifetime.
- **Trend computation is intentionally simple** — two-bar price direction plus a flat sentiment average, not a validated price/sentiment correlation model. See the [aggregation-trend-computation](../user-stories/aggregation-trend-computation.md) story's scope decisions.
- **No cache warming or hit-rate metrics** — entries populate lazily on first request; nothing observes how effective the cache actually is yet.
- **`/history` has no pagination** — a ticker with years of daily bars comes back as one (potentially large) response. Fine at current data volumes.
- **No authentication/authorization on the REST API** — it's a local-dev API for now. See the [rest-api](../user-stories/rest-api.md) story.
- **First run of a persistent consumer group processes the entire topic backlog.** `spring.kafka.consumer.group-id=aggregation-service` with `auto-offset-reset=earliest` means the *first* time this service (under that group id) connects, it consumes and persists every message ever published to `marketpulse.prices.raw`/`marketpulse.sentiment.raw` — including old test messages from other components' test runs that were never previously consumed by a committed group. Expected Kafka behavior, not a bug, but worth knowing before being surprised by unfamiliar tickers showing up in Postgres after a first run.

## Assumptions made

- **Spring Boot 4.1.0, Java 21**, scaffolded via `start.spring.io`'s `starter.zip` API rather than a local Spring Boot CLI/IDE — no system-wide Maven was installed, so the generated project's bundled Maven wrapper (`./mvnw`) is what makes this buildable at all in this environment. Both the Kafka and Flyway dependencies have renamed Initializr ids under this Boot version: `kafka` resolves to `spring-boot-starter-kafka` (not `spring-kafka`), and `flyway` resolves to `spring-boot-starter-flyway` (not raw `flyway-core`). Checked via `curl -s https://start.spring.io/pom.xml?dependencies=...` rather than guessing, after being burned by exactly this once already on Kafka.
- **`spring-boot-starter-flyway` alone isn't enough for Postgres** — it must be paired with `flyway-database-postgresql` (Flyway splits per-database support into separate modules). Missing it doesn't fail loudly at the dependency level: the app boots, Flyway silently never creates the `flyway_schema_history` table or runs any migration, and every query then fails with `relation "price_bars" does not exist` — misleading, since it looks like an application-level bug rather than a missing dependency. Confirmed by checking Postgres directly (`\dt`, and querying `flyway_schema_history`) rather than trusting that a clean test run meant the schema existed. Adding `flyway-database-postgresql` alone still isn't sufficient either — without `spring-boot-starter-flyway`, Spring Boot's Flyway auto-configuration doesn't activate at all, so the two must be used together.
- **An explicit `ObjectMapper` `@Bean` was required** (`config/JacksonConfig.java`), added back when there was no web starter on the classpath at all — Spring Boot's Jackson auto-configuration didn't provide one even with `jackson-databind` present directly. The web starter (and springdoc) arrived later, with this story's REST API; the explicit bean was kept rather than removed, since it's still the one place `JavaTimeModule`/`Jdk8Module` registration is made explicit rather than left to auto-configuration's classpath scanning.
- Same `127.0.0.1` (not `localhost`) bootstrap-server convention as the Python components — see [event-stream.md](event-stream.md) for why; the underlying cause (this machine resolving `localhost` to IPv6 first) applies to any Kafka client on this machine, not just the Python ones.
- The live integration tests use Spring Kafka's own `ContainerTestUtils.waitForAssignment(...)` (rather than a hand-rolled polling loop, like the Python integration tests used) to avoid the same class of rebalance-timing bug found in the sentiment pipeline's tests — publishing before a fresh consumer group's rebalance completes means the message can be missed under `"latest"` offset reset.
- **Plain JDBC over JPA/Hibernate** for the persistence layer — see the [postgres-persistence-layer](../user-stories/postgres-persistence-layer.md) story's design decisions for why (native upsert fit, no relational complexity that would justify an ORM here).
- **`StringRedisTemplate` + Jackson over Spring Data Redis repositories** — same reasoning: this is "get/put/evict one JSON blob by key," not a query need. `jackson-datatype-jdk8` was added so `TrendSummary`'s `Optional<Double>` fields serialize directly rather than needing a parallel cache-only DTO with nullable fields.
- **No reachability guard added for `AggregationServiceApplicationTests`/`AggregationServiceIntegrationTest`** (unlike Kafka and Postgres, which do have one) — deliberately. Spring's Lettuce Redis client connects lazily, so context startup doesn't need Redis at all, and `TrendSummaryCache`'s defensive design means functional correctness doesn't either. Skipping the guard here is itself a live proof that graceful degradation works, not just an oversight — confirmed by literally stopping Redis and watching both tests still pass.
- Redis's Spring Initializr id (`data-redis`) resolves to `spring-boot-starter-data-redis` under this Boot version — matched the already-known `-data-jpa`-style convention, no surprise this time (unlike Kafka and Flyway).
- **`web`'s Initializr id resolves to `spring-boot-starter-webmvc`, not `spring-boot-starter-web`** — a third instance of the same renaming pattern as Kafka and Flyway. Checked via the Initializr `pom.xml` endpoint before adding it, same as always by this point.
- **Spring Boot 4.1's module split goes deeper than starter names — whole test-support classes moved packages.** `@WebMvcTest` moved from `org.springframework.boot.test.autoconfigure.web.servlet` to `org.springframework.boot.webmvc.test.autoconfigure`; `TestRestTemplate` moved from `org.springframework.boot.test.web.client` to `org.springframework.boot.resttestclient`. Both compiled cleanly once the classes were located directly inside the already-resolved dependency jars (`unzip -l` the jar and grep for the class name) rather than guessed from Boot 3-era muscle memory. `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`) was unchanged.
- **`TestRestTemplate` needs an explicit `@AutoConfigureTestRestTemplate` annotation now** — it's no longer auto-registered just from `@SpringBootTest(webEnvironment = RANDOM_PORT)`.
- **`TestRestTemplate`'s own dependency, `RestTemplateBuilder`, lives in a separate module (`org.springframework.boot:spring-boot-restclient`) that isn't pulled in transitively by `spring-boot-starter-webmvc-test`.** Without it, the whole `@SpringBootTest` context fails with `NoClassDefFoundError` on `RestTemplateBuilder` — added explicitly, test scope only (it's only needed for `TestRestTemplate`, nothing in production code uses it).
- **`springdoc-openapi-starter-webmvc-ui:2.8.6`** (the latest available on Maven Central at the time) worked without any compatibility issues against Spring Boot 4.1/Framework 7, despite Maven Central not (yet) showing a version explicitly targeting Boot 4 — springdoc's own README already documents Boot 4 support and demos, so this was tried directly rather than assumed to be broken; it just worked.
