# User Story: PostgreSQL Schema + Persistence Layer

**Component:** Aggregation Service · **Architecture:** [aggregation service internals](../architecture/aggregation-service.md) · **Replaces the storage inside:** [aggregation-trend-computation](aggregation-trend-computation.md)

```
As a MarketPulse operator
I want price bars and sentiment scores durably persisted in PostgreSQL
So that trend data survives an Aggregation Service restart instead of
being lost every time the in-memory store is wiped
```

## Design decisions

- **Plain JDBC (`NamedParameterJdbcTemplate`), not JPA/Hibernate.** Both tables are simple upsert-by-natural-key storage with no relationships, joins, or lazy-loading needs — exactly what Postgres's native `INSERT ... ON CONFLICT ... DO UPDATE` handles cleanly. Pulling in full ORM machinery for two flat tables would be complexity with no payoff; see Anvil's own principle (reach for an abstraction only when it removes real duplication).
- **`TrendCalculator` does not change at all.** Only `TrendStore`'s internals change — from `ConcurrentHashMap` to two repositories backed by Postgres. `TrendStore`'s public API (`recordPriceBar`, `recordSentiment`, `getTrendSummary`) is unchanged, so `PriceEventListener`/`SentimentEventListener` need no changes either. This was the explicit plan already written into [architecture/aggregation-service.md](../architecture/aggregation-service.md) before this story existed.
- **Idempotency moves from application code to the database.** Previously `TrendStore` enforced "same key overwrites" itself via map keys. Now `INSERT ... ON CONFLICT (ticker, trade_date) DO UPDATE` (and the sentiment equivalent) enforces the same guarantee at the schema level — the composite primary key *is* the idempotency mechanism.
- **Flyway for schema migrations**, not `ddl-auto=update` — versioned, explicit, reviewable SQL rather than Hibernate inferring the schema from entities (which also wouldn't apply here since there are no JPA entities).
- **Still no windowing/retention policy.** Every price bar and sentiment score ever consumed is still kept forever — now durably, which makes this more of a real concern than when it was memory-bounded by process lifetime. Still explicitly out of scope for this slice (same reasoning as before: no real load yet to design a retention policy against).
- **`@SpringBootTest`-based tests now require both Kafka and Postgres reachable**, not just Kafka — `TrendStore`'s beans need a working `DataSource` to construct at all, so the whole context fails to start without Postgres. Both existing Spring-context tests get an explicit reachability check (fail with a clear skip, not a cryptic context-loading exception).

## Acceptance criteria

- Given the Aggregation Service starts against a fresh Postgres database, when Flyway runs, then the `price_bars` and `sentiment_scores` tables exist with the documented schema.
- Given a price bar is recorded, when the same `(ticker, trade_date)` is recorded again with different values (redelivery with updated data, or a genuine correction), then the stored row reflects the latest values — one row per key, not a duplicate.
- Given a sentiment score is recorded, when the same `(ticker, article_uuid)` is recorded again, then the stored row is unchanged in effect (upsert with the same values) — the average sentiment computed from it is not skewed by double-counting.
- Given price bars exist for a ticker, when queried, then they come back ordered by trade date (so `TrendCalculator` can find the latest two without re-sorting).
- Given no data exists for a ticker, when queried, then an empty result is returned, not an exception — `TrendStore.getTrendSummary` continues to report this as `Optional.empty()`.
- Given the Aggregation Service is restarted, when it comes back up, then previously recorded price/sentiment data is still queryable — the actual point of this story, verified live by restarting the process (or reconnecting fresh repositories) against the same running Postgres.
- Given real price and sentiment events flow through Kafka into the Aggregation Service, when a trend summary is requested, then it reflects data that was actually written to and read back from Postgres — verified against the real local database, not a mock.

## Test coverage

| Acceptance criterion | Test(s) |
|---|---|
| Schema created by Flyway | Implicit in every `@SpringBootTest` succeeding — context can't start if migration fails |
| Price bar upsert overwrites by `(ticker, trade_date)` | `PersistenceIntegrationTest.upsertingSamePriceBarKeyOverwritesRatherThanDuplicates` |
| Sentiment score upsert is idempotent | `PersistenceIntegrationTest.upsertingSameSentimentKeyIsIdempotent` |
| Price bars returned ordered by trade date | `PersistenceIntegrationTest.findByTickerReturnsPriceBarsOrderedByDate` |
| Unknown ticker → empty result, not an exception | `PersistenceIntegrationTest.findByTickerReturnsEmptyListForUnknownTicker` (both repositories) |
| `TrendStore` orchestrates repositories correctly (mocked) | `TrendStoreTest.getTrendSummaryReturnsEmptyWhenNoPriceBars`, `TrendStoreTest.getTrendSummaryComputesFromRepositoryData`, `TrendStoreTest.recordPriceBarDelegatesToRepository`, `TrendStoreTest.recordSentimentDelegatesToRepository` |
| Data survives a restart | Live-verified manually (see [reference doc](../reference/aggregation-service.md)) — write, restart the process, read back |
| Full pipeline (Kafka → listener → Postgres → trend summary) reflects real data | `AggregationServiceIntegrationTest.consumesRealPriceAndSentimentEventsAndComputesTrend` (already existing, now exercising real Postgres instead of in-memory maps) |

## Status

Implemented and tested — see [reference/aggregation-service.md](../reference/aggregation-service.md).
