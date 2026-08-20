# User Story: Aggregation Service — Consumer + Trend Computation

**Component:** Aggregation Service (new — first story for this component) · **Architecture:** [aggregation service internals](../architecture/aggregation-service.md) · **Consumes:** [kafka-event-producers](kafka-event-producers.md)'s `marketpulse.prices.raw`, [sentiment-scoring-pipeline](sentiment-scoring-pipeline.md)'s `marketpulse.sentiment.raw`

```
As a MarketPulse operator
I want price and sentiment events consumed and combined into a per-ticker
trend summary
So that a trend summary is available for a ticker without needing to
manually correlate raw events myself
```

## Scope decisions

- **In-memory storage, not Postgres.** "PostgreSQL schema + persistence layer" is a separate, later roadmap item. This slice computes and holds trend summaries in memory; persistence replaces the storage layer later without changing the computation logic.
- **No REST endpoint.** "REST API" is a separate, later roadmap item. This slice is a Kafka consumer only — verified via direct calls to the trend store and live Kafka integration tests, not HTTP.
- **Long-running `@KafkaListener` consumers, not a bounded batch CLI.** Unlike the Python components (deliberately bounded/batch — see their own stories), a Spring Boot service continuously consuming its input topics is the idiomatic, correct target shape for this component per the [root README](../../README.md#architecture) — there's no reason to fight that here.
- **No code dependency on the scraper or sentiment pipeline.** Same principle as the sentiment pipeline: only the documented `marketpulse.prices.raw` / `marketpulse.sentiment.raw` JSON schemas are depended on, not any Python code.
- **Idempotent by design, not by deduplication tracking.** Price bars are stored keyed by `(ticker, tradeDate)` — reprocessing the same bar just overwrites it with the same value. Sentiment scores are stored keyed by `(ticker, articleUuid)` for the same reason — this also avoids a subtler bug: naively accumulating a running average would double-count a redelivered sentiment event, which keying by article UUID and recomputing the average from current stored values on every read avoids entirely.
- **Trend computation is intentionally simple for this slice**: price direction from the two most recent closes, plus an average sentiment score and label breakdown, presented together — not a claim of statistically validated price/sentiment correlation. Deeper correlation modeling is a later concern once there's real data to validate an approach against.

## Acceptance criteria

- Given a `marketpulse.prices.raw` message for a ticker, when consumed, then that ticker's stored price history includes the new bar, keyed by trade date.
- Given the same price bar message is consumed twice (redelivery), when processed, then the stored history is unchanged — not duplicated, not double-counted.
- Given a `marketpulse.sentiment.raw` message for a ticker, when consumed, then that ticker's average sentiment score reflects the new value.
- Given the same sentiment message is consumed twice (redelivery), when processed, then the average sentiment score is unchanged — not skewed by double-counting.
- Given at least two price bars exist for a ticker, when a trend summary is requested, then it includes the latest close and the percent change from the previous close.
- Given only one price bar exists for a ticker, when a trend summary is requested, then the percent change is absent/null, not a division-by-zero error or a fabricated value.
- Given no data exists for a ticker at all, when a trend summary is requested, then that's reported as absent (e.g. empty `Optional`), not an exception.
- Given a malformed Kafka message (missing required fields, wrong types), when consumed, then it's logged and skipped — it does not crash the listener or block subsequent messages.
- Given real price and sentiment events published to Kafka, when the service consumes them, then a trend summary reflecting both is computed — verified against a real broker, not just unit-level mocks.

## Explicitly out of scope

- No REST API — a later roadmap item.
- No persistence — in-memory only for this slice; unbounded growth over a long-running process is a known limitation, not solved here.
- No sophisticated price/sentiment correlation model — see scope decisions above.
- No windowing/retention policy for historical data — every bar and every sentiment score is kept forever in memory for this slice.

## Test coverage

| Acceptance criterion | Test(s) |
|---|---|
| Price bar consumed → stored, keyed by trade date | `TrendStoreTest.recordsPriceBarByTradeDate` |
| Duplicate price bar → no duplication | `TrendStoreTest.recordingSamePriceBarTwiceIsIdempotent` |
| Sentiment event consumed → average reflects it | `TrendStoreTest.recordsSentimentByArticleUuid` |
| Duplicate sentiment event → average unaffected | `TrendStoreTest.recordingSameSentimentTwiceIsIdempotent` |
| ≥2 bars → close + percent change present | `TrendCalculatorTest.computesPercentChangeFromTwoMostRecentBars` |
| 1 bar → percent change absent, no error | `TrendCalculatorTest.singleBarHasNoPercentChange` |
| No data for ticker → absent, not an exception | `TrendStoreTest.getTrendSummaryReturnsEmptyForUnknownTicker` |
| Malformed message → logged, skipped, listener keeps running | `PriceEventListenerTest.malformedMessageIsSkippedNotThrown`, `SentimentEventListenerTest.malformedMessageIsSkippedNotThrown` |
| Real broker round-trip: consume → compute → reflected in trend summary | `AggregationServiceIntegrationTest.consumesRealPriceAndSentimentEventsAndComputesTrend` |

## Status

Implemented and tested — see [reference/aggregation-service.md](../reference/aggregation-service.md).
