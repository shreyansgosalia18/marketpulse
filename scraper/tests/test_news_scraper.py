from unittest.mock import patch

import pytest

from marketpulse_scraper.news_client import YahooNewsRequestError
from marketpulse_scraper.news_scraper import NewsScraper

VALID_PAYLOAD = {
    "quotes": [{"symbol": "AAPL", "longname": "Apple Inc."}],
    "news": [
        {
            "uuid": "abc-123",
            "title": "Apple unveils new product",
            "publisher": "Reuters",
            "link": "https://example.com/apple-news",
            "providerPublishTime": 1704200400,
        }
    ],
}

EMPTY_PAYLOAD = {"news": []}

MIXED_RELEVANCE_PAYLOAD = {
    "quotes": [{"symbol": "AAPL", "longname": "Apple Inc."}],
    "news": [
        {
            "uuid": "relevant-1",
            "title": "Apple stock rises after earnings",
            "publisher": "Reuters",
            "link": "https://example.com/relevant",
            "providerPublishTime": 1704200400,
        },
        {
            "uuid": "irrelevant-1",
            "title": "Dow futures rise on broad tech rally",
            "publisher": "Bloomberg",
            "link": "https://example.com/irrelevant",
            "providerPublishTime": 1704200500,
        },
    ],
}

UNRESOLVED_TICKER_PAYLOAD = {
    "quotes": [],
    "news": [
        {
            "uuid": "trending-1",
            "title": "Some unrelated trending headline",
            "publisher": "Bloomberg",
            "link": "https://example.com/trending",
            "providerPublishTime": 1704200400,
        }
    ],
}


def test_empty_watchlist_is_rejected_at_construction():
    with pytest.raises(ValueError):
        NewsScraper([])


@patch("marketpulse_scraper.news_scraper.fetch_news_json")
def test_returns_articles_for_a_ticker_with_news(mock_fetch):
    mock_fetch.return_value = VALID_PAYLOAD

    [result] = NewsScraper(["AAPL"]).fetch_news()

    assert result.ok
    assert result.error is None
    assert len(result.articles) == 1
    assert result.articles[0].ticker == "AAPL"


@patch("marketpulse_scraper.news_scraper.fetch_news_json")
def test_ticker_with_no_news_is_ok_with_empty_article_list(mock_fetch):
    mock_fetch.return_value = EMPTY_PAYLOAD

    [result] = NewsScraper(["QUIETSTOCK"]).fetch_news()

    assert result.ok
    assert result.error is None
    assert result.articles == []


@patch("marketpulse_scraper.news_scraper.fetch_news_json")
def test_network_failure_yields_error_result_not_an_exception(mock_fetch):
    mock_fetch.side_effect = YahooNewsRequestError("timed out")

    [result] = NewsScraper(["AAPL"]).fetch_news()

    assert not result.ok
    assert result.articles is None
    assert "timed out" in result.error


@patch("marketpulse_scraper.news_scraper.fetch_news_json")
def test_malformed_response_shape_yields_error_result_not_an_exception(mock_fetch):
    mock_fetch.return_value = {"quotes": []}  # missing "news" entirely

    [result] = NewsScraper(["AAPL"]).fetch_news()

    assert not result.ok
    assert result.articles is None
    assert result.error is not None


@patch("marketpulse_scraper.news_scraper.fetch_news_json")
def test_one_failing_ticker_does_not_affect_the_others_in_the_batch(mock_fetch):
    def fake_fetch(ticker, **kwargs):
        if ticker == "BADTICKER":
            raise YahooNewsRequestError("boom")
        return VALID_PAYLOAD

    mock_fetch.side_effect = fake_fetch

    results = NewsScraper(["AAPL", "BADTICKER", "MSFT"]).fetch_news()
    by_ticker = {r.ticker: r for r in results}

    assert by_ticker["AAPL"].ok
    assert by_ticker["MSFT"].ok
    assert not by_ticker["BADTICKER"].ok


@patch("marketpulse_scraper.news_scraper.fetch_news_json")
def test_irrelevant_articles_are_filtered_out(mock_fetch):
    mock_fetch.return_value = MIXED_RELEVANCE_PAYLOAD

    [result] = NewsScraper(["AAPL"]).fetch_news()

    assert result.ok
    titles = [a.title for a in result.articles]
    assert "Apple stock rises after earnings" in titles
    assert "Dow futures rise on broad tech rally" not in titles


@patch("marketpulse_scraper.news_scraper.fetch_news_json")
def test_unresolvable_ticker_yields_empty_articles_not_fallback_noise(mock_fetch):
    mock_fetch.return_value = UNRESOLVED_TICKER_PAYLOAD

    [result] = NewsScraper(["THISISNOTAREALTICKER"]).fetch_news()

    assert result.ok
    assert result.articles == []
