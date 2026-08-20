# User Story: Kafka Event Schema + Producers

**Component:** [Event Stream](../reference/event-stream.md) · **Architecture:** [scraper internals](../architecture/scraper.md) · **Depends on:** [scraper-price-ingestion](scraper-price-ingestion.md), [scraper-news-ingestion](scraper-news-ingestion.md), [local-dev-docker-compose](local-dev-docker-compose.md)

```
As a MarketPulse operator
I want the scraper's price and news results published onto Kafka as they're fetched
So that the sentiment pipeline and aggregation service can consume them
independently, without depending on the scraper's own process
```

## Acceptance criteria

- Given a successful price fetch, when published, then each `PriceBar` appears on the `marketpulse.prices.raw` topic as a JSON message matching the price event schema, keyed by ticker.
- Given a successful news fetch, when published, then each `NewsArticle` appears on the `marketpulse.news.raw` topic as a JSON message matching the news event schema, keyed by ticker.
- Given a `ScrapeResult`/`NewsScrapeResult` where `ok=False` (the scraper already failed for that ticker), when publishing runs, then nothing is published for that ticker — publishing only ever forwards data that was actually fetched successfully.
- Given messages are keyed by ticker, when multiple bars/articles for the same ticker are published, then they land on the same partition — order is preserved per ticker (Kafka only guarantees order within a partition).
- Given one message fails to publish (e.g. a transient broker error), when publishing a batch, then that failure is recorded but does not stop the rest of the batch from being attempted — same per-item isolation principle as the scraper itself.
- Given the Kafka broker is completely unreachable, when publishing is attempted, then the failure is reported clearly (a `PublishResult` with errors), not silently swallowed or left to hang indefinitely.
- Given a message actually lands on the topic, when a real consumer reads it back, then it deserializes to the same ticker/values that were published — verified against a real broker, not just that `.send()` didn't raise.

## Explicitly out of scope

- No sentiment events yet — that's the [sentiment scoring pipeline](../../README.md#roadmap)'s job once it exists; this story only covers the scraper's own raw price/news output.
- No consumer code — this is the producer side only. The aggregation service and sentiment pipeline will each own their own consumer when they're built.
- No schema registry (e.g. Confluent Schema Registry) — schemas are plain JSON with a `schema_version` field for now. Revisit if/when schema evolution actually becomes a problem.
- No retry/backoff on publish failure — a failure is reported, not automatically retried. Matches the scraper's own "report clearly, don't guess at recovery" approach.

## Test coverage

| Acceptance criterion | Test(s) |
|---|---|
| Price bars published to `marketpulse.prices.raw`, correct schema | `test_price_bar_to_event_matches_schema`, `test_publishes_bars_from_successful_results` |
| News articles published to `marketpulse.news.raw`, correct schema | `test_news_article_to_event_matches_schema`, `test_publishes_articles_from_successful_results` |
| Failed `ScrapeResult`/`NewsScrapeResult` → nothing published for that ticker | `test_skips_failed_results` (price), `test_skips_failed_news_results` (news) |
| Messages keyed by ticker | `test_price_bar_to_event_matches_schema` (key assertion), `test_news_article_to_event_matches_schema` (key assertion) |
| One publish failure doesn't stop the rest of the batch | `test_one_publish_failure_does_not_stop_the_batch` |
| Unreachable broker → reported clearly, not swallowed | `test_publish_failure_is_recorded_not_raised` |
| Real broker round-trip (produce → consume back) | `test_published_price_bar_is_consumable_from_real_broker`, `test_published_news_article_is_consumable_from_real_broker` (skipped automatically if Kafka isn't reachable) |

## Status

Implemented and tested — see [reference/event-stream.md](../reference/event-stream.md) for the schema, topics, and usage.
