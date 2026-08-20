"""Integration test against a real Kafka broker.

Requires the local Docker Compose stack (see docs/reference/local-dev.md).
Skipped automatically if Kafka isn't reachable. Deliberately seeds the
input topic with a raw KafkaProducer, not the scraper's own producer -
proving this component only depends on the documented
marketpulse.news.raw schema, not on scraper code.
"""

import json
import socket
import uuid

import pytest
from kafka import KafkaConsumer, KafkaProducer

from marketpulse_sentiment.events import NEWS_TOPIC, SENTIMENT_TOPIC
from marketpulse_sentiment.pipeline import DEFAULT_API_VERSION, DEFAULT_BOOTSTRAP_SERVERS, SentimentPipeline


def _kafka_is_reachable() -> bool:
    host, port = DEFAULT_BOOTSTRAP_SERVERS.split(":")
    try:
        with socket.create_connection((host, int(port)), timeout=2):
            return True
    except OSError:
        return False


pytestmark = pytest.mark.skipif(not _kafka_is_reachable(), reason="Kafka broker not reachable at 127.0.0.1:9092")


def _wait_for_partition_assignment(consumer, timeout_ms: int = 10000, poll_interval_ms: int = 500) -> None:
    """Poll until the consumer group's rebalance actually completes.

    A single poll() isn't reliable - a brand-new consumer group can take
    longer than one short poll to join, sync, and get partitions assigned
    with the "latest" offset reset applied. Publishing before that finishes
    means the message arrives before the consumer is actually positioned
    to see it, so it's silently missed instead of consumed.
    """
    waited = 0
    while not consumer.assignment() and waited < timeout_ms:
        consumer.poll(timeout_ms=poll_interval_ms)
        waited += poll_interval_ms
    assert consumer.assignment(), f"Consumer group never got partition assignment within {timeout_ms}ms"


def test_published_sentiment_event_is_consumable_from_real_broker():
    ticker = f"TEST-{uuid.uuid4().hex[:8]}"
    article_uuid = str(uuid.uuid4())
    news_payload = {
        "schema_version": 1,
        "event_type": "news_article",
        "ticker": ticker,
        "uuid": article_uuid,
        "title": "Company beats estimates, shares soar on strong outlook",
        "publisher": "Test Publisher",
        "link": "https://example.com/test",
        "published_at": "2024-01-02T12:00:00+00:00",
    }

    # Fresh consumer group + "latest": arm it BEFORE publishing the seed
    # message, so it doesn't miss it, and it never reprocesses the full
    # history of every news event other tests have published to this topic.
    pipeline = SentimentPipeline(consumer_group=f"test-{uuid.uuid4().hex[:8]}", auto_offset_reset="latest")
    _wait_for_partition_assignment(pipeline._consumer)

    output_consumer = KafkaConsumer(
        SENTIMENT_TOPIC,
        bootstrap_servers=DEFAULT_BOOTSTRAP_SERVERS,
        api_version=DEFAULT_API_VERSION,
        group_id=f"test-output-{uuid.uuid4().hex[:8]}",
        auto_offset_reset="latest",
        consumer_timeout_ms=20000,
        value_deserializer=lambda v: json.loads(v.decode("utf-8")),
    )
    _wait_for_partition_assignment(output_consumer)

    seed_producer = KafkaProducer(
        bootstrap_servers=DEFAULT_BOOTSTRAP_SERVERS,
        api_version=DEFAULT_API_VERSION,
        key_serializer=lambda k: k.encode("utf-8"),
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
    )
    try:
        seed_producer.send(NEWS_TOPIC, key=ticker, value=news_payload).get(timeout=10)
        seed_producer.flush()
    finally:
        seed_producer.close()

    try:
        result = pipeline.run_once()
    finally:
        pipeline.close()

    assert result.consumed >= 1
    assert result.published >= 1

    try:
        for message in output_consumer:
            if message.value.get("ticker") == ticker:
                assert message.value["article_uuid"] == article_uuid
                assert message.value["sentiment"] == "positive"
                return
        pytest.fail(f"No sentiment event for ticker {ticker!r} consumed in time")
    finally:
        output_consumer.close()
