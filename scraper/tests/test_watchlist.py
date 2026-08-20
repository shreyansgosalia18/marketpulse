from unittest.mock import patch

import pytest

from marketpulse_scraper.watchlist import WatchlistScraper
from marketpulse_scraper.yahoo_client import YahooRequestError

VALID_PAYLOAD = {
    "chart": {
        "result": [
            {
                "timestamp": [1704200400],
                "indicators": {
                    "quote": [
                        {
                            "open": [150.00],
                            "high": [151.50],
                            "low": [149.25],
                            "close": [150.75],
                            "volume": [1000000],
                        }
                    ]
                },
            }
        ],
        "error": None,
    }
}

NOT_FOUND_PAYLOAD = {"chart": {"result": None, "error": {"code": "Not Found", "description": "No data found"}}}


def test_empty_watchlist_is_rejected_at_construction():
    with pytest.raises(ValueError):
        WatchlistScraper([])


@patch("marketpulse_scraper.watchlist.fetch_history_json")
def test_returns_bars_for_a_ticker_with_valid_data(mock_fetch):
    mock_fetch.return_value = VALID_PAYLOAD

    [result] = WatchlistScraper(["AAPL"]).fetch_history()

    assert result.ok
    assert result.error is None
    assert len(result.bars) == 1
    assert result.bars[0].ticker == "AAPL"


@patch("marketpulse_scraper.watchlist.fetch_history_json")
def test_network_failure_yields_error_result_not_an_exception(mock_fetch):
    mock_fetch.side_effect = YahooRequestError("timed out")

    [result] = WatchlistScraper(["AAPL"]).fetch_history()

    assert not result.ok
    assert result.bars is None
    assert "timed out" in result.error


@patch("marketpulse_scraper.watchlist.fetch_history_json")
def test_unknown_ticker_yields_error_result_not_an_exception(mock_fetch):
    mock_fetch.return_value = NOT_FOUND_PAYLOAD

    [result] = WatchlistScraper(["NONEXISTENTTICKER"]).fetch_history()

    assert not result.ok
    assert result.bars is None
    assert result.error is not None


@patch("marketpulse_scraper.watchlist.fetch_history_json")
def test_one_failing_ticker_does_not_affect_the_others_in_the_batch(mock_fetch):
    def fake_fetch(ticker, **kwargs):
        if ticker == "BADTICKER":
            raise YahooRequestError("boom")
        return VALID_PAYLOAD

    mock_fetch.side_effect = fake_fetch

    results = WatchlistScraper(["AAPL", "BADTICKER", "MSFT"]).fetch_history()
    by_ticker = {r.ticker: r for r in results}

    assert by_ticker["AAPL"].ok
    assert by_ticker["MSFT"].ok
    assert not by_ticker["BADTICKER"].ok
