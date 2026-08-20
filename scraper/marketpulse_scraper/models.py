from dataclasses import dataclass
from datetime import date


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
