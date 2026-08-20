from .models import NewsEvent, SentimentEvent

NEWS_TOPIC = "marketpulse.news.raw"
SENTIMENT_TOPIC = "marketpulse.sentiment.raw"
SCHEMA_VERSION = 1

_REQUIRED_NEWS_FIELDS = ("ticker", "uuid", "title")


def parse_news_event(payload: dict) -> NewsEvent:
    """Parse a marketpulse.news.raw message into a NewsEvent.

    Only depends on the documented schema (docs/reference/event-stream.md),
    not on the scraper's NewsArticle model - this component consumes the
    Kafka contract, not scraper code.
    """
    missing = [field for field in _REQUIRED_NEWS_FIELDS if field not in payload]
    if missing:
        raise ValueError(f"news event missing required field(s): {missing}")
    return NewsEvent(ticker=payload["ticker"], article_uuid=payload["uuid"], title=payload["title"])


def sentiment_event_to_message(event: SentimentEvent) -> dict:
    """Serialize a SentimentEvent into the marketpulse.sentiment.raw JSON schema."""
    return {
        "schema_version": SCHEMA_VERSION,
        "event_type": "sentiment_score",
        "ticker": event.ticker,
        "article_uuid": event.article_uuid,
        "sentiment": event.label,
        "compound_score": event.compound_score,
        "scored_at": event.scored_at.isoformat(),
    }
