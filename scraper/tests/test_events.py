from datetime import date, datetime, timezone

from marketpulse_scraper.events import (
    NEWS_TOPIC,
    PRICE_TOPIC,
    SCHEMA_VERSION,
    news_article_to_event,
    price_bar_to_event,
)
from marketpulse_scraper.models import NewsArticle, PriceBar


def test_price_bar_to_event_matches_schema():
    bar = PriceBar(ticker="AAPL", trade_date=date(2024, 1, 2), open=150.0, high=151.5, low=149.25, close=150.75, volume=1000000)

    event = price_bar_to_event(bar)

    assert event == {
        "schema_version": SCHEMA_VERSION,
        "event_type": "price_bar",
        "ticker": "AAPL",
        "trade_date": "2024-01-02",
        "open": 150.0,
        "high": 151.5,
        "low": 149.25,
        "close": 150.75,
        "volume": 1000000,
    }
    # The event's ticker is what a producer would key the Kafka message on.
    assert event["ticker"] == bar.ticker


def test_news_article_to_event_matches_schema():
    article = NewsArticle(
        ticker="AAPL",
        uuid="abc-123",
        title="Apple unveils new product",
        publisher="Reuters",
        link="https://example.com/apple-news",
        published_at=datetime(2024, 1, 2, 12, 0, tzinfo=timezone.utc),
    )

    event = news_article_to_event(article)

    assert event == {
        "schema_version": SCHEMA_VERSION,
        "event_type": "news_article",
        "ticker": "AAPL",
        "uuid": "abc-123",
        "title": "Apple unveils new product",
        "publisher": "Reuters",
        "link": "https://example.com/apple-news",
        "published_at": "2024-01-02T12:00:00+00:00",
    }
    assert event["ticker"] == article.ticker


def test_topic_names_are_namespaced():
    assert PRICE_TOPIC == "marketpulse.prices.raw"
    assert NEWS_TOPIC == "marketpulse.news.raw"
