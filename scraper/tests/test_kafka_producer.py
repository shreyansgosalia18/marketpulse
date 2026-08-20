from datetime import date, datetime, timezone
from unittest.mock import MagicMock, patch

from kafka.errors import KafkaError

from marketpulse_scraper.events import NEWS_TOPIC, PRICE_TOPIC
from marketpulse_scraper.kafka_producer import MarketPulseProducer
from marketpulse_scraper.models import NewsArticle, PriceBar
from marketpulse_scraper.news_scraper import NewsScrapeResult
from marketpulse_scraper.watchlist import ScrapeResult

BAR = PriceBar(ticker="AAPL", trade_date=date(2024, 1, 2), open=150.0, high=151.5, low=149.25, close=150.75, volume=1_000_000)
ARTICLE = NewsArticle(
    ticker="AAPL",
    uuid="abc-123",
    title="Apple unveils new product",
    publisher="Reuters",
    link="https://example.com/apple-news",
    published_at=datetime(2024, 1, 2, 12, 0, tzinfo=timezone.utc),
)


def _producer_with_mock_client(send_side_effect=None):
    with patch("marketpulse_scraper.kafka_producer.KafkaProducer") as mock_kafka_producer_cls:
        mock_client = MagicMock()
        if send_side_effect is not None:
            mock_client.send.side_effect = send_side_effect
        else:
            mock_client.send.return_value.get.return_value = None
        mock_kafka_producer_cls.return_value = mock_client
        producer = MarketPulseProducer()
        return producer, mock_client


def test_publishes_bars_from_successful_results():
    producer, mock_client = _producer_with_mock_client()
    results = [ScrapeResult(ticker="AAPL", bars=[BAR, BAR], error=None)]

    outcome = producer.publish_price_results(results)

    assert outcome.topic == PRICE_TOPIC
    assert outcome.published == 2
    assert outcome.ok
    assert mock_client.send.call_count == 2


def test_skips_failed_results():
    producer, mock_client = _producer_with_mock_client()
    results = [ScrapeResult(ticker="BADTICKER", bars=None, error="no data")]

    outcome = producer.publish_price_results(results)

    assert outcome.published == 0
    assert outcome.errors == []
    mock_client.send.assert_not_called()


def test_publishes_articles_from_successful_news_results():
    producer, mock_client = _producer_with_mock_client()
    results = [NewsScrapeResult(ticker="AAPL", articles=[ARTICLE], error=None)]

    outcome = producer.publish_news_results(results)

    assert outcome.topic == NEWS_TOPIC
    assert outcome.published == 1
    mock_client.send.assert_called_once()


def test_skips_failed_news_results():
    producer, mock_client = _producer_with_mock_client()
    results = [NewsScrapeResult(ticker="BADTICKER", articles=None, error="unreachable")]

    outcome = producer.publish_news_results(results)

    assert outcome.published == 0
    mock_client.send.assert_not_called()


def test_one_publish_failure_does_not_stop_the_batch():
    call_count = {"n": 0}

    def flaky_send(topic, key=None, value=None):
        call_count["n"] += 1
        future = MagicMock()
        if call_count["n"] == 2:
            future.get.side_effect = KafkaError("transient failure")
        else:
            future.get.return_value = None
        return future

    producer, mock_client = _producer_with_mock_client(send_side_effect=flaky_send)
    results = [ScrapeResult(ticker="AAPL", bars=[BAR, BAR, BAR], error=None)]

    outcome = producer.publish_price_results(results)

    assert outcome.published == 2
    assert len(outcome.errors) == 1
    assert mock_client.send.call_count == 3


def test_publish_failure_is_recorded_not_raised():
    def always_fails(topic, key=None, value=None):
        future = MagicMock()
        future.get.side_effect = KafkaError("broker unreachable")
        return future

    producer, mock_client = _producer_with_mock_client(send_side_effect=always_fails)
    results = [ScrapeResult(ticker="AAPL", bars=[BAR], error=None)]

    outcome = producer.publish_price_results(results)

    assert outcome.published == 0
    assert not outcome.ok
    assert "broker unreachable" in outcome.errors[0]
