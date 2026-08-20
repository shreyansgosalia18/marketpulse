from datetime import date

import pytest

from marketpulse_scraper.parser import NoDataError, parse_price_history

VALID_PAYLOAD = {
    "chart": {
        "result": [
            {
                "timestamp": [1704200400, 1704286800],
                "indicators": {
                    "quote": [
                        {
                            "open": [150.00, 150.75],
                            "high": [151.50, 152.00],
                            "low": [149.25, 150.00],
                            "close": [150.75, 151.90],
                            "volume": [1000000, 1200000],
                        }
                    ]
                },
            }
        ],
        "error": None,
    }
}


def test_parses_valid_payload_into_price_bars_in_row_order():
    bars = parse_price_history("AAPL", VALID_PAYLOAD)

    assert len(bars) == 2
    first = bars[0]
    assert first.ticker == "AAPL"
    assert first.trade_date == date(2024, 1, 2)
    assert first.open == 150.00
    assert first.high == 151.50
    assert first.low == 149.25
    assert first.close == 150.75
    assert first.volume == 1000000


def test_skips_rows_with_null_ohlcv_values():
    payload = {
        "chart": {
            "result": [
                {
                    "timestamp": [1704200400, 1704286800],
                    "indicators": {
                        "quote": [
                            {
                                "open": [150.00, None],
                                "high": [151.50, None],
                                "low": [149.25, None],
                                "close": [150.75, None],
                                "volume": [1000000, None],
                            }
                        ]
                    },
                }
            ],
            "error": None,
        }
    }

    bars = parse_price_history("AAPL", payload)

    assert len(bars) == 1
    assert bars[0].trade_date == date(2024, 1, 2)


def test_raises_no_data_error_when_chart_reports_an_error():
    payload = {"chart": {"result": None, "error": {"code": "Not Found", "description": "No data found, symbol may be delisted"}}}

    with pytest.raises(NoDataError):
        parse_price_history("NONEXISTENTTICKER", payload)


def test_raises_no_data_error_when_result_is_empty():
    payload = {"chart": {"result": [], "error": None}}

    with pytest.raises(NoDataError):
        parse_price_history("AAPL", payload)


def test_raises_no_data_error_when_all_rows_are_null():
    payload = {
        "chart": {
            "result": [
                {
                    "timestamp": [1704200400],
                    "indicators": {
                        "quote": [{"open": [None], "high": [None], "low": [None], "close": [None], "volume": [None]}]
                    },
                }
            ],
            "error": None,
        }
    }

    with pytest.raises(NoDataError):
        parse_price_history("AAPL", payload)


def test_raises_value_error_when_quote_data_is_missing():
    payload = {"chart": {"result": [{"timestamp": [1704200400], "indicators": {"quote": []}}], "error": None}}

    with pytest.raises(ValueError):
        parse_price_history("AAPL", payload)


def test_raises_value_error_when_ohlcv_arrays_have_mismatched_length():
    payload = {
        "chart": {
            "result": [
                {
                    "timestamp": [1704200400, 1704286800],
                    "indicators": {
                        "quote": [{"open": [150.00], "high": [151.50], "low": [149.25], "close": [150.75], "volume": [1000000]}]
                    },
                }
            ],
            "error": None,
        }
    }

    with pytest.raises(ValueError):
        parse_price_history("AAPL", payload)
