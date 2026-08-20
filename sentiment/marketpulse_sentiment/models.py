from dataclasses import dataclass
from datetime import datetime

POSITIVE = "positive"
NEGATIVE = "negative"
NEUTRAL = "neutral"


@dataclass(frozen=True)
class NewsEvent:
    """A consumed marketpulse.news.raw message, parsed independently of the
    scraper's own NewsArticle model - this component only depends on the
    documented Kafka schema, not on scraper code."""

    ticker: str
    article_uuid: str
    title: str


@dataclass(frozen=True)
class SentimentScore:
    """Result of scoring one piece of text."""

    label: str
    compound_score: float


@dataclass(frozen=True)
class SentimentEvent:
    """A scored article, ready to publish to marketpulse.sentiment.raw."""

    ticker: str
    article_uuid: str
    label: str
    compound_score: float
    scored_at: datetime
