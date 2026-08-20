from datetime import datetime, timezone

import pytest

from marketpulse_scraper.news_parser import extract_company_name, parse_news_articles

VALID_PAYLOAD = {
    "news": [
        {
            "uuid": "abc-123",
            "title": "Apple unveils new product",
            "publisher": "Reuters",
            "link": "https://example.com/apple-news",
            "providerPublishTime": 1704200400,
            "type": "STORY",
        },
        {
            "uuid": "def-456",
            "title": "Apple earnings beat expectations",
            "publisher": "Bloomberg",
            "link": "https://example.com/apple-earnings",
            "providerPublishTime": 1704286800,
            "type": "STORY",
        },
    ]
}


def test_parses_valid_payload_into_news_articles_in_order():
    articles = parse_news_articles("AAPL", VALID_PAYLOAD)

    assert len(articles) == 2
    first = articles[0]
    assert first.ticker == "AAPL"
    assert first.uuid == "abc-123"
    assert first.title == "Apple unveils new product"
    assert first.publisher == "Reuters"
    assert first.link == "https://example.com/apple-news"
    assert first.published_at == datetime.fromtimestamp(1704200400, tz=timezone.utc)


def test_empty_news_list_returns_empty_list_not_an_error():
    articles = parse_news_articles("AAPL", {"news": []})

    assert articles == []


def test_raises_value_error_when_news_field_is_missing():
    with pytest.raises(ValueError):
        parse_news_articles("AAPL", {"quotes": []})


def test_raises_value_error_when_news_field_is_not_a_list():
    with pytest.raises(ValueError):
        parse_news_articles("AAPL", {"news": "not a list"})


def test_skips_malformed_articles_but_keeps_valid_ones():
    payload = {
        "news": [
            {"uuid": "abc-123", "title": "Missing publisher"},  # missing required fields
            VALID_PAYLOAD["news"][0],
            "not even a dict",
        ]
    }

    articles = parse_news_articles("AAPL", payload)

    assert len(articles) == 1
    assert articles[0].uuid == VALID_PAYLOAD["news"][0]["uuid"]


def test_extract_company_name_finds_matching_quote():
    payload = {"quotes": [{"symbol": "AAPL", "longname": "Apple Inc."}]}

    assert extract_company_name("AAPL", payload) == "Apple Inc."


def test_extract_company_name_returns_none_when_no_matching_quote():
    payload = {"quotes": [{"symbol": "MSFT", "longname": "Microsoft Corporation"}]}

    assert extract_company_name("AAPL", payload) is None


def test_extract_company_name_returns_none_when_quotes_field_is_missing():
    assert extract_company_name("AAPL", {"news": []}) is None
