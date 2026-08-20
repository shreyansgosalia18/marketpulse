from dataclasses import dataclass

from .models import NewsArticle
from .news_client import YahooNewsRequestError, fetch_news_json
from .news_parser import extract_company_name, parse_news_articles
from .relevance import filter_relevant


@dataclass(frozen=True)
class NewsScrapeResult:
    """Outcome of fetching one ticker's news: either articles (possibly empty) or an error, never both."""

    ticker: str
    articles: list[NewsArticle] | None
    error: str | None

    @property
    def ok(self) -> bool:
        return self.error is None


class NewsScraper:
    """Fetches financial news for a configurable watchlist of tickers.

    Unlike price history, an empty article list is a normal, successful
    result for a ticker — only request failures and malformed responses are
    reported as errors. A failure on one ticker never prevents the rest of
    the watchlist from being fetched.
    """

    def __init__(self, tickers: list[str]):
        if not tickers:
            raise ValueError("Watchlist must contain at least one ticker")
        self._tickers = list(tickers)

    def fetch_news(self) -> list[NewsScrapeResult]:
        return [self._fetch_one(ticker) for ticker in self._tickers]

    def _fetch_one(self, ticker: str) -> NewsScrapeResult:
        try:
            payload = fetch_news_json(ticker)
            articles = parse_news_articles(ticker, payload)
            company_name = extract_company_name(ticker, payload)
            articles = filter_relevant(ticker, company_name, articles)
        except (YahooNewsRequestError, ValueError) as exc:
            return NewsScrapeResult(ticker=ticker, articles=None, error=str(exc))
        return NewsScrapeResult(ticker=ticker, articles=articles, error=None)
