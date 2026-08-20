from marketpulse_scraper.models import NewsArticle
from marketpulse_scraper.relevance import core_company_name, filter_relevant


def _article(title: str) -> NewsArticle:
    return NewsArticle(
        ticker="AAPL",
        uuid="uuid-1",
        title=title,
        publisher="Some Publisher",
        link="https://example.com/a",
        published_at=None,
    )


def test_keeps_article_mentioning_ticker_symbol():
    articles = [_article("AAPL shares rise after earnings beat")]

    result = filter_relevant("AAPL", "Apple Inc.", articles)

    assert result == articles


def test_keeps_article_mentioning_company_name():
    articles = [_article("Apple unveils new product lineup")]

    result = filter_relevant("AAPL", "Apple Inc.", articles)

    assert result == articles


def test_matches_company_name_without_corporate_suffix():
    assert core_company_name("Apple Inc.") == "Apple"
    assert core_company_name("Meta Platforms, Inc.") == "Meta Platforms"
    assert core_company_name("Alphabet Inc.") == "Alphabet"

    articles = [_article("Apple stock climbs on strong iPhone demand")]
    result = filter_relevant("AAPL", "Apple Inc.", articles)

    assert result == articles


def test_excludes_article_not_mentioning_ticker_or_company():
    articles = [_article("Dow Jones Futures: S&P 500 rises on tech rally")]

    result = filter_relevant("AAPL", "Apple Inc.", articles)

    assert result == []


def test_unresolvable_ticker_yields_empty_articles_not_error():
    articles = [_article("Some unrelated trending story")]

    result = filter_relevant("THISISNOTAREALTICKER", None, articles)

    assert result == []


def test_filtering_to_zero_articles_is_still_ok():
    articles = [_article("Unrelated headline about something else entirely")]

    result = filter_relevant("MSFT", "Microsoft Corporation", articles)

    assert result == []
