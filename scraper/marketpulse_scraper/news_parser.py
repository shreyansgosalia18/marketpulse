from datetime import datetime, timezone

from .models import NewsArticle

_REQUIRED_FIELDS = ("uuid", "title", "publisher", "link", "providerPublishTime")


def parse_news_articles(ticker: str, payload: dict) -> list[NewsArticle]:
    """Parse a Yahoo Finance search-endpoint JSON payload into NewsArticle rows.

    Absence of news for a ticker is a normal outcome (an empty list), not an
    error. Only a malformed response *shape* raises; a single malformed
    article within an otherwise valid list is skipped rather than failing
    the whole ticker.
    """
    if "news" not in payload:
        raise ValueError(f"Unexpected response shape for {ticker!r}: missing 'news' field")

    raw_items = payload["news"]
    if not isinstance(raw_items, list):
        raise ValueError(f"Unexpected response shape for {ticker!r}: 'news' is not a list")

    articles: list[NewsArticle] = []
    for item in raw_items:
        if not isinstance(item, dict) or any(field not in item for field in _REQUIRED_FIELDS):
            continue
        articles.append(
            NewsArticle(
                ticker=ticker,
                uuid=item["uuid"],
                title=item["title"],
                publisher=item["publisher"],
                link=item["link"],
                published_at=datetime.fromtimestamp(item["providerPublishTime"], tz=timezone.utc),
            )
        )
    return articles


def extract_company_name(ticker: str, payload: dict) -> str | None:
    """Look up the company/fund name for `ticker` from the same payload's `quotes` array.

    Returns None if the query didn't resolve to a matching quote — which is
    exactly the signal that lets relevance filtering reject an unrecognized
    or bogus ticker instead of keeping the source's generic fallback results.
    """
    for quote in payload.get("quotes", []):
        if isinstance(quote, dict) and str(quote.get("symbol", "")).upper() == ticker.upper():
            return quote.get("longname") or quote.get("shortname")
    return None
