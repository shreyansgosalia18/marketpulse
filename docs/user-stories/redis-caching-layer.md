# User Story: Redis Caching Layer

**Component:** Aggregation Service · **Architecture:** [aggregation service internals](../architecture/aggregation-service.md) · **Sits in front of:** [postgres-persistence-layer](postgres-persistence-layer.md)

```
As a MarketPulse operator
I want repeated lookups of the same ticker's trend summary served from
cache instead of re-querying and recomputing from PostgreSQL every time
So that frequently-queried tickers get fast, low-load lookups
```

## Design decisions

- **Cache the computed `TrendSummary`, not raw rows.** The expensive part of `getTrendSummary` is the two Postgres round-trips, not `TrendCalculator`'s in-memory computation — caching the final answer, keyed by ticker, is what actually saves work.
- **Invalidate-on-write, not TTL-only.** This system continuously ingests real-time Kafka data — a summary that stays stale for a fixed TTL after new data arrives would defeat the purpose of a "trend" service. So `recordPriceBar`/`recordSentiment` evict that ticker's cache entry immediately; a cache miss then recomputes fresh from Postgres and repopulates. A short TTL (5 minutes) is kept as a defensive safety net in case an eviction is ever missed, not as the primary consistency mechanism.
- **`StringRedisTemplate` + Jackson, not Spring Data Redis repositories.** Same reasoning as the Postgres layer's plain-JDBC decision: this is "get/put/evict one JSON blob by key," not a rich query need — the repository abstraction would add indirection without removing duplication.
- **`TrendSummaryCache` is a new collaborator alongside the two Postgres repositories**, not a decorator wrapping `TrendStore`. `TrendStore` remains the single orchestrator: check cache → hit returns immediately; miss queries Postgres, computes, populates cache. Same shape as the existing `PriceBarRepository`/`SentimentScoreRepository` collaborators.
- **`jackson-datatype-jdk8` added to serialize `TrendSummary` directly** (it has `Optional<Double>` fields) rather than introducing a parallel cache-only DTO with nullable fields. One small, well-known Jackson module vs. a whole parallel type — the smaller change.
- **A cache failure must never break the read/write path.** Postgres is the source of truth; Redis is purely a performance optimization. Every cache operation (get/put/evict) is wrapped so a Redis exception is logged and treated as "cache did nothing" — reads fall through to Postgres, writes just skip eviction (bounded by the TTL safety net above, not left unbounded). A service that's fully working except its cache is down should keep working, not start failing requests.

## Acceptance criteria

- Given a ticker's trend summary has never been requested, when `getTrendSummary` is called, then it queries Postgres, computes the summary, and populates the cache — a cache miss.
- Given a ticker's trend summary is already cached, when `getTrendSummary` is called again, then it returns the cached value without querying Postgres — verified by seeding the cache directly with a value that could not possibly come from Postgres (no matching rows exist there) and confirming that's what's returned.
- Given a ticker has a cached trend summary, when a new price bar or sentiment score is recorded for that ticker, then the cache entry is evicted — the next `getTrendSummary` call recomputes fresh rather than returning stale data.
- Given no data exists for a ticker in either the cache or Postgres, when `getTrendSummary` is called, then it returns `Optional.empty()` — same behavior as before this story, cache or no cache.
- Given Redis is unreachable, when `getTrendSummary`/`recordPriceBar`/`recordSentiment` are called, then they still succeed using Postgres as the source of truth — the cache failure is logged, not thrown, and never surfaces as an error to the caller.

## Explicitly out of scope

- No cache warming/preloading — entries populate lazily on first request.
- No cache-level metrics (hit rate, etc.) — a later observability concern if it becomes relevant.

## Test coverage

| Acceptance criterion | Test(s) |
|---|---|
| Cache miss → queries Postgres, computes, populates cache | `TrendStoreTest.getTrendSummaryPopulatesCacheOnMiss` |
| Cache hit → returns cached value without querying Postgres | `TrendStoreTest.getTrendSummaryReturnsCachedValueWithoutQueryingRepositories`, `CachingIntegrationTest.cachedValueIsReturnedEvenWhenPostgresHasNoMatchingData` (live) |
| Recording new data evicts the cache | `TrendStoreTest.recordPriceBarDelegatesToRepositoryAndEvictsCache`, `TrendStoreTest.recordSentimentDelegatesToRepositoryAndEvictsCache`, `CachingIntegrationTest.recordingNewDataEvictsStaleCacheEntry` (live) |
| No data anywhere → `Optional.empty()` | `TrendStoreTest.getTrendSummaryReturnsEmptyForUnknownTicker` (existing, still passes unchanged) |
| Real Redis round-trip (serialize/deserialize `TrendSummary` with `Optional` fields) | `CachingIntegrationTest.putThenGetRoundTripsCorrectly` (live) |
| Redis failure doesn't break reads/writes | `TrendSummaryCacheTest.getReturnsEmptyRatherThanThrowingWhenRedisOperationFails`, `TrendSummaryCacheTest.putAndEvictSwallowRedisFailuresRatherThanThrowing` |

## Status

Implemented and tested — see [reference/aggregation-service.md](../reference/aggregation-service.md).
