import requests

YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/{ticker}"
_HEADERS = {"User-Agent": "Mozilla/5.0"}


class YahooRequestError(Exception):
    """Raised when the HTTP request to Yahoo Finance fails, times out, or errors server-side."""


def fetch_history_json(
    ticker: str, *, range_: str = "1mo", interval: str = "1d", timeout: float = 10.0
) -> dict:
    """Fetch the raw chart JSON payload for a ticker from Yahoo Finance.

    A 404 for an unknown ticker still returns a well-formed JSON error body,
    so it's returned to the caller rather than raised here — parse_price_history
    is what decides "no data" vs. a real request failure.
    """
    url = YAHOO_CHART_URL.format(ticker=ticker)
    try:
        response = requests.get(
            url,
            params={"range": range_, "interval": interval},
            headers=_HEADERS,
            timeout=timeout,
        )
    except requests.RequestException as exc:
        raise YahooRequestError(f"Request for {ticker!r} failed: {exc}") from exc

    if response.status_code >= 500:
        raise YahooRequestError(f"Request for {ticker!r} failed: server error {response.status_code}")

    try:
        return response.json()
    except ValueError as exc:
        raise YahooRequestError(
            f"Request for {ticker!r} returned non-JSON response (status {response.status_code})"
        ) from exc
