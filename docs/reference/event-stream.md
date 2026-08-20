# Reference: Event Stream (Kafka)

**User story:** [kafka-event-producers](../user-stories/kafka-event-producers.md) · **Architecture:** [event stream internals](../architecture/event-stream.md)

The scraper publishes its price and news results onto Kafka as it fetches them, so the sentiment pipeline and aggregation service (once built) can consume independently — see the [system overview](../architecture/system-overview.md).

> **Status:** producers implemented and tested against a real broker. No consumers exist yet — that's the sentiment pipeline and aggregation service's job when they're built.

## Topics and schema

| Topic | Produced from | Key |
|---|---|---|
| `marketpulse.prices.raw` | `PriceBar` | ticker |
| `marketpulse.news.raw` | `NewsArticle` (already relevance-filtered) | ticker |

Both are keyed by ticker so all messages for one ticker land on the same partition — order is preserved per ticker. Messages are plain JSON with a `schema_version` field for now (no schema registry — see [Known limitations](#known-limitations--not-yet-built)).

```json
// marketpulse.prices.raw
{
  "schema_version": 1,
  "event_type": "price_bar",
  "ticker": "AAPL",
  "trade_date": "2024-01-02",
  "open": 150.0,
  "high": 151.5,
  "low": 149.25,
  "close": 150.75,
  "volume": 1000000
}
```

```json
// marketpulse.news.raw
{
  "schema_version": 1,
  "event_type": "news_article",
  "ticker": "AAPL",
  "uuid": "abc-123",
  "title": "Apple unveils new product",
  "publisher": "Reuters",
  "link": "https://example.com/apple-news",
  "published_at": "2024-01-02T12:00:00+00:00"
}
```

## Usage

```python
from marketpulse_scraper.kafka_producer import MarketPulseProducer
from marketpulse_scraper.watchlist import WatchlistScraper
from marketpulse_scraper.news_scraper import NewsScraper

price_results = WatchlistScraper(["AAPL", "MSFT"]).fetch_history()
news_results = NewsScraper(["AAPL", "MSFT"]).fetch_news()

producer = MarketPulseProducer()
try:
    price_outcome = producer.publish_price_results(price_results)
    news_outcome = producer.publish_news_results(news_results)
    producer.flush()
finally:
    producer.close()

print(price_outcome.published, "price messages,", len(price_outcome.errors), "errors")
```

CLI, for manual/ad-hoc runs (requires the [local Kafka stack](local-dev.md) running):

```
cd scraper
.venv\Scripts\python publish_to_kafka.py AAPL MSFT
```

Only successfully-fetched data is published — a ticker that failed at the scraper level (`ok=False`) is skipped, not published as a partial/error event. A failure publishing one message is recorded in `PublishResult.errors` and doesn't stop the rest of the batch.

## Testing

```
cd scraper
.venv\Scripts\python -m pytest tests/ -v
```

- `tests/test_events.py` — schema serialization (unit, no broker needed).
- `tests/test_kafka_producer.py` — publish logic with the Kafka client mocked: skips failed results, isolates per-message failures, reports broker-unreachable clearly (unit, no broker needed).
- `tests/test_kafka_producer_integration.py` — **live**, against a real broker: publishes a uniquely-tickered message, consumes it back, and asserts the round-trip. Automatically skipped (not failed) if Kafka isn't reachable at `127.0.0.1:9092`, so the rest of the suite stays green without Docker running.

All of the above were also independently verified with Kafka's own tooling (`kafka-console-consumer.sh`), not just through this project's own test code.

## Known limitations / not yet built

- No consumers — messages accumulate on the topics with nothing reading them yet.
- No schema registry — a producer-side bug could publish a message that doesn't match the documented schema, and nothing would catch it until a consumer breaks. Revisit if schema evolution becomes a real problem.
- No retry/backoff on publish failure — reported once, not retried. Matches the scraper's own philosophy (report clearly, don't guess at recovery).
- Single-partition topics (matches the single-broker dev Kafka) — fine for now, but partition count would need revisiting before this sees any real throughput or a multi-consumer-instance setup.

## Assumptions made

- **`kafka-python` 3.x doesn't work reliably here — pinned to `2.3.2`.** kafka-python 3.0+ is a from-scratch async rewrite (an internal event loop, not just a sync wrapper). Running a `KafkaConsumer` and `KafkaProducer` concurrently in the same process — exactly what the integration test does — caused the producer's connection to time out entirely (`Unable to bootstrap`), with the library's own logs warning about a blocked coroutine event loop. A quick single-producer smoke test looked fine on 3.x, which would have been a false "it works" if a real produce+consume round-trip hadn't been tested. `requirements.txt` pins `kafka-python>=2.0` — actually resolves to `2.3.2`, the current tip of the classic synchronous line; `2.0.2` specifically doesn't import at all on this project's Python 3.14 (a vendored `six` packaging bug in that release).
- **`api_version` must be set explicitly.** kafka-python's automatic version-negotiation probe against this Kafka 3.8.0 broker times out (~60-70s) rather than failing fast. Passing `api_version=(3, 8)` skips the probe and connects immediately. (kafka-python only tracks major.minor — passing `(3, 8, 0)` works but logs a harmless "not supported, using (3, 8)" warning on every connection.)
- **Bootstrap/advertised address must be `127.0.0.1`, not `localhost`.** On this machine "localhost" resolves to the IPv6 loopback (`::1`) first. Docker's port mapping and Kafka's `KAFKA_ADVERTISED_LISTENERS` were both only listening on IPv4 — the initial bootstrap connection would sometimes succeed anyway, but the *advertised* address Kafka hands back to the client for actual produce requests was `localhost:9092`, which then failed to connect over IPv6 and timed out metadata refresh on every publish. Fixed in two places: `docker-compose.yml`'s `KAFKA_ADVERTISED_LISTENERS` now uses `${BIND_HOST}` (`127.0.0.1`) instead of the literal string `localhost`, and `kafka_producer.py`'s `DEFAULT_BOOTSTRAP_SERVERS` is `127.0.0.1:9092`. Caught only because the integration test asserted a real consumed message, not just that `.send()` didn't raise.
