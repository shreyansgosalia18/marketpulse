# Architecture: Aggregation Service

**Fits into:** [system overview](system-overview.md) · **Reference:** [aggregation service reference doc](../reference/aggregation-service.md)

## Consumer flow

```mermaid
flowchart TD
    KP["marketpulse.prices.raw"] --> PL[PriceEventListener]
    KS["marketpulse.sentiment.raw"] --> SL[SentimentEventListener]
    PL -->|"parse (JacksonConfig ObjectMapper)"| PLE[PriceBarRecord]
    SL -->|"parse (JacksonConfig ObjectMapper)"| SLE[SentimentRecord]
    PL -. malformed message: log + skip .-> PL
    SL -. malformed message: log + skip .-> SL
    PLE --> TS[TrendStore]
    SLE --> TS
    TS -->|"keyed by (ticker, tradeDate)<br/>and (ticker, articleUuid) - idempotent"| STORE[(In-memory maps)]
    TS -->|"getTrendSummary(ticker)"| TC[TrendCalculator]
    TC --> SUMMARY["TrendSummary (latestClose, percentChange,<br/>averageSentiment, sentimentLabelCounts)"]
```

Both listeners are thin: parse the message, delegate to `TrendStore`. A malformed message is logged and skipped — never thrown, so it can't stop the listener from processing the rest of the topic. `TrendCalculator` is a pure, stateless function from stored history to a `TrendSummary`, independently unit-testable with no Spring context.

| Module | Responsibility |
|---|---|
| `trend/PriceBarRecord.java`, `trend/SentimentRecord.java` | Stored, deduplicated history per ticker |
| `trend/TrendSummary.java` | Computed output — latest close, percent change, average sentiment, label counts |
| `trend/TrendCalculator.java` | Pure computation, no I/O, no Spring |
| `trend/TrendStore.java` | Idempotent in-memory storage + orchestrates calculation |
| `kafka/PriceEventListener.java`, `kafka/SentimentEventListener.java` | Thin `@KafkaListener`s — parse, delegate, isolate failures |
| `kafka/dto/*` | Jackson-mapped DTOs for the two Kafka JSON schemas |
| `config/JacksonConfig.java` | Explicit `ObjectMapper` bean (see reference doc's Known Limitations for why this was needed) |

## Why idempotent-by-storage, not deduplication tracking

Kafka delivery is at-least-once — any consumer must expect redelivery. Rather than tracking "have I seen this message before," both listeners store data keyed by something naturally unique to the *content* (`(ticker, tradeDate)` for price bars, `(ticker, articleUuid)` for sentiment): reprocessing the same message overwrites the same key with the same value, which is a no-op in effect. `TrendCalculator.compute()` then always recomputes fresh from current stored state rather than maintaining an incremental running average — this is what avoids a redelivered sentiment score silently double-counting toward the average.

## Why a long-running service, not a bounded batch

Unlike the Python components (deliberately bounded/batch — see their own architecture docs), `@KafkaListener` is Spring Kafka's idiomatic continuous-consumption model, and a persistently-running Aggregation Service is the actual target shape per the [root README](../../README.md#architecture) — there was no reason to fight that here the way the Python demo scripts intentionally did.
