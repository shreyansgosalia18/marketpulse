from .models import NewsArticle, PriceBar

SCHEMA_VERSION = 1

PRICE_TOPIC = "marketpulse.prices.raw"
NEWS_TOPIC = "marketpulse.news.raw"


def price_bar_to_event(bar: PriceBar) -> dict:
    """Serialize a PriceBar into the marketpulse.prices.raw JSON schema."""
    return {
        "schema_version": SCHEMA_VERSION,
        "event_type": "price_bar",
        "ticker": bar.ticker,
        "trade_date": bar.trade_date.isoformat(),
        "open": bar.open,
        "high": bar.high,
        "low": bar.low,
        "close": bar.close,
        "volume": bar.volume,
    }


def news_article_to_event(article: NewsArticle) -> dict:
    """Serialize a NewsArticle into the marketpulse.news.raw JSON schema."""
    return {
        "schema_version": SCHEMA_VERSION,
        "event_type": "news_article",
        "ticker": article.ticker,
        "uuid": article.uuid,
        "title": article.title,
        "publisher": article.publisher,
        "link": article.link,
        "published_at": article.published_at.isoformat(),
    }
