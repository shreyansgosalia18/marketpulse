from dataclasses import dataclass

from .models import PriceBar
from .parser import NoDataError, parse_price_history
from .yahoo_client import YahooRequestError, fetch_history_json


@dataclass(frozen=True)
class ScrapeResult:
    """Outcome of fetching one ticker's history: either bars or an error, never both."""

    ticker: str
    bars: list[PriceBar] | None
    error: str | None

    @property
    def ok(self) -> bool:
        return self.error is None


class WatchlistScraper:
    """Fetches price/volume history for a configurable watchlist of tickers.

    A failure on one ticker (bad symbol, network error, malformed data) is
    reported as an error result for that ticker only — it never prevents the
    rest of the watchlist from being fetched.
    """

    def __init__(self, tickers: list[str]):
        if not tickers:
            raise ValueError("Watchlist must contain at least one ticker")
        self._tickers = list(tickers)

    def fetch_history(self) -> list[ScrapeResult]:
        return [self._fetch_one(ticker) for ticker in self._tickers]

    def _fetch_one(self, ticker: str) -> ScrapeResult:
        try:
            payload = fetch_history_json(ticker)
            bars = parse_price_history(ticker, payload)
        except (YahooRequestError, NoDataError, ValueError) as exc:
            return ScrapeResult(ticker=ticker, bars=None, error=str(exc))
        return ScrapeResult(ticker=ticker, bars=bars, error=None)
