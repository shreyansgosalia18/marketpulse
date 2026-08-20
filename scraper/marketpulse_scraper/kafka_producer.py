import json
from dataclasses import dataclass, field

from kafka import KafkaProducer
from kafka.errors import KafkaError

from .events import NEWS_TOPIC, PRICE_TOPIC, news_article_to_event, price_bar_to_event
from .news_scraper import NewsScrapeResult
from .watchlist import ScrapeResult

# 127.0.0.1, not "localhost" - on this stack "localhost" resolves to the
# IPv6 loopback first, which Docker's port mapping and Kafka's advertised
# listener don't accept, causing a metadata-fetch timeout on every produce.
DEFAULT_BOOTSTRAP_SERVERS = "127.0.0.1:9092"

# kafka-python's automatic API-version negotiation times out against our
# Kafka 3.8.0 broker (observed: ~70s timeout, every time). Setting this
# explicitly skips that negotiation entirely and connects instantly.
# kafka-python only tracks (major, minor), not patch - (3, 8, 0) still works
# but logs a harmless "not supported, using (3, 8)" warning on every connect.
DEFAULT_API_VERSION = (3, 8)


@dataclass
class PublishResult:
    """Outcome of publishing a batch: how many messages landed, and any per-message failures."""

    topic: str
    published: int = 0
    errors: list[str] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.errors


class MarketPulseProducer:
    """Publishes the scraper's price/news results onto Kafka.

    Only successfully-fetched data is published — a ScrapeResult/
    NewsScrapeResult with an error is skipped, not published as a partial
    or error event. A failure publishing one message is recorded and does
    not stop the rest of the batch from being attempted, matching the
    scraper's own per-item isolation.
    """

    def __init__(self, bootstrap_servers: str = DEFAULT_BOOTSTRAP_SERVERS, api_version: tuple = DEFAULT_API_VERSION):
        self._producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            api_version=api_version,
            key_serializer=lambda k: k.encode("utf-8"),
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        )

    def publish_price_results(self, results: list[ScrapeResult]) -> PublishResult:
        outcome = PublishResult(topic=PRICE_TOPIC)
        for result in results:
            if not result.ok:
                continue
            for bar in result.bars:
                self._publish_one(outcome, key=bar.ticker, event=price_bar_to_event(bar))
        return outcome

    def publish_news_results(self, results: list[NewsScrapeResult]) -> PublishResult:
        outcome = PublishResult(topic=NEWS_TOPIC)
        for result in results:
            if not result.ok:
                continue
            for article in result.articles:
                self._publish_one(outcome, key=article.ticker, event=news_article_to_event(article))
        return outcome

    def _publish_one(self, outcome: PublishResult, key: str, event: dict) -> None:
        try:
            future = self._producer.send(outcome.topic, key=key, value=event)
            future.get(timeout=10)
        except KafkaError as exc:
            outcome.errors.append(f"{key}: {exc}")
        else:
            outcome.published += 1

    def flush(self) -> None:
        self._producer.flush()

    def close(self) -> None:
        self._producer.close()
