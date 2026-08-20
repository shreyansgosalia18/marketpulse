from dataclasses import dataclass
from datetime import date, datetime


@dataclass(frozen=True)
class PriceBar:
    """One day of OHLCV price history for a single ticker."""

    ticker: str
    trade_date: date
    open: float
    high: float
    low: float
    close: float
    volume: int


@dataclass(frozen=True)
class NewsArticle:
    """One news item associated with a ticker."""

    ticker: str
    uuid: str
    title: str
    publisher: str
    link: str
    published_at: datetime
