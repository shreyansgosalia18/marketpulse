# Architecture: Event Stream

**Fits into:** [system overview](system-overview.md) · **Reference:** [event stream reference doc](../reference/event-stream.md)

## Producer flow

```mermaid
flowchart TD
    CLI[publish_to_kafka.py] --> WLS[WatchlistScraper]
    CLI --> NS[NewsScraper]
    WLS -->|"list[ScrapeResult]"| MPP[MarketPulseProducer]
    NS -->|"list[NewsScrapeResult]"| MPP
    MPP -->|"skips ok=False results"| EV[events.price_bar_to_event /<br/>events.news_article_to_event]
    EV -->|"JSON, keyed by ticker"| KP["marketpulse.prices.raw"]
    EV -->|"JSON, keyed by ticker"| KN["marketpulse.news.raw"]
    MPP -->|per-message failures| RESULT["PublishResult(published, errors)"]
```

`MarketPulseProducer` only publishes data from results where `ok=True` — a scraper-level failure for a ticker never becomes a Kafka message. A publish failure for one message is caught and recorded in `PublishResult.errors`, not raised, so it never stops the rest of the batch — the same per-item isolation used throughout the scraper.

| Module | Responsibility |
|---|---|
| `events.py` | Defines topic names, `schema_version`, and serializes `PriceBar`/`NewsArticle` into the JSON event schema |
| `kafka_producer.py` | `MarketPulseProducer` — publishes a batch of scrape results, isolating per-message failures into `PublishResult` |

## Downstream (not built yet)

```mermaid
flowchart LR
    KP["marketpulse.prices.raw"] -.-> AGG["Aggregation Service<br/>⬜ planned"]
    KN["marketpulse.news.raw"] -.-> SENT["Sentiment Pipeline<br/>⬜ planned"]
    SENT -.->|sentiment events, topic TBD| AGG
```

No consumers exist yet. When the sentiment pipeline is built, it will consume `marketpulse.news.raw` and produce its own sentiment-scored events (topic name and schema to be defined as part of that story); the Aggregation Service will consume both raw prices and sentiment events to compute trends.
