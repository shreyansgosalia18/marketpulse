from datetime import datetime, timezone

import pytest

from marketpulse_sentiment.events import parse_news_event, sentiment_event_to_message
from marketpulse_sentiment.models import SentimentEvent


def test_parses_valid_news_event():
    payload = {
        "schema_version": 1,
        "event_type": "news_article",
        "ticker": "AAPL",
        "uuid": "abc-123",
        "title": "Apple unveils new product",
        "publisher": "Reuters",
        "link": "https://example.com/apple-news",
        "published_at": "2024-01-02T12:00:00+00:00",
    }

    event = parse_news_event(payload)

    assert event.ticker == "AAPL"
    assert event.article_uuid == "abc-123"
    assert event.title == "Apple unveils new product"


def test_raises_value_error_when_required_field_missing():
    with pytest.raises(ValueError):
        parse_news_event({"ticker": "AAPL", "title": "Some headline"})  # missing uuid


def test_sentiment_event_to_message_matches_schema():
    event = SentimentEvent(
        ticker="AAPL",
        article_uuid="abc-123",
        label="positive",
        compound_score=0.65,
        scored_at=datetime(2024, 1, 2, 12, 5, tzinfo=timezone.utc),
    )

    message = sentiment_event_to_message(event)

    assert message == {
        "schema_version": 1,
        "event_type": "sentiment_score",
        "ticker": "AAPL",
        "article_uuid": "abc-123",
        "sentiment": "positive",
        "compound_score": 0.65,
        "scored_at": "2024-01-02T12:05:00+00:00",
    }
