"""Integration tests against a real Kafka broker.

Requires the local Docker Compose stack (see docs/reference/local-dev.md).
Skipped automatically if Kafka isn't reachable, so the main test suite
still runs green without Docker.
"""

import socket
import uuid
from datetime import date, datetime, timezone

import pytest
from kafka import KafkaConsumer

from marketpulse_scraper.events import NEWS_TOPIC, PRICE_TOPIC
from marketpulse_scraper.kafka_producer import DEFAULT_API_VERSION, DEFAULT_BOOTSTRAP_SERVERS, MarketPulseProducer
from marketpulse_scraper.models import NewsArticle, PriceBar
from marketpulse_scraper.news_scraper import NewsScrapeResult
from marketpulse_scraper.watchlist import ScrapeResult


def _kafka_is_reachable() -> bool:
    host, port = DEFAULT_BOOTSTRAP_SERVERS.split(":")
    try:
        with socket.create_connection((host, int(port)), timeout=2):
            return True
    except OSError:
        return False


pytestmark = pytest.mark.skipif(not _kafka_is_reachable(), reason="Kafka broker not reachable at localhost:9092")


def test_published_price_bar_is_consumable_from_real_broker():
    # Unique ticker so this test's message is unambiguous even with other traffic on the topic.
    ticker = f"TEST-{uuid.uuid4().hex[:8]}"
    bar = PriceBar(ticker=ticker, trade_date=date(2024, 1, 2), open=1.0, high=2.0, low=0.5, close=1.5, volume=42)

    consumer_probe = _start_consumer_before_publish(PRICE_TOPIC)

    producer = MarketPulseProducer()
    try:
        outcome = producer.publish_price_results([ScrapeResult(ticker=ticker, bars=[bar], error=None)])
        producer.flush()
    finally:
        producer.close()

    assert outcome.ok
    assert outcome.published == 1

    consumed = _wait_for_ticker(consumer_probe, ticker)
    assert consumed["ticker"] == ticker
    assert consumed["close"] == 1.5


def test_published_news_article_is_consumable_from_real_broker():
    ticker = f"TEST-{uuid.uuid4().hex[:8]}"
    article = NewsArticle(
        ticker=ticker,
        uuid=str(uuid.uuid4()),
        title="Integration test article",
        publisher="Test Publisher",
        link="https://example.com/test",
        published_at=datetime(2024, 1, 2, 12, 0, tzinfo=timezone.utc),
    )

    consumer_probe = _start_consumer_before_publish(NEWS_TOPIC)

    producer = MarketPulseProducer()
    try:
        outcome = producer.publish_news_results([NewsScrapeResult(ticker=ticker, articles=[article], error=None)])
        producer.flush()
    finally:
        producer.close()

    assert outcome.ok
    assert outcome.published == 1

    consumed = _wait_for_ticker(consumer_probe, ticker)
    assert consumed["ticker"] == ticker
    assert consumed["title"] == "Integration test article"


def _start_consumer_before_publish(topic: str) -> KafkaConsumer:
    import json

    consumer = KafkaConsumer(
        topic,
        bootstrap_servers=DEFAULT_BOOTSTRAP_SERVERS,
        api_version=DEFAULT_API_VERSION,
        auto_offset_reset="latest",
        consumer_timeout_ms=20000,
        value_deserializer=lambda v: json.loads(v.decode("utf-8")),
    )
    # Force partition assignment before the producer sends, so "latest" doesn't
    # miss a message published in the gap between subscribing and assignment.
    consumer.poll(timeout_ms=1000)
    return consumer


def _wait_for_ticker(consumer: KafkaConsumer, ticker: str) -> dict:
    try:
        for message in consumer:
            if message.value.get("ticker") == ticker:
                return message.value
    finally:
        consumer.close()
    raise TimeoutError(f"No message for ticker {ticker!r} consumed in time")
