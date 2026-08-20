import requests

YAHOO_SEARCH_URL = "https://query2.finance.yahoo.com/v1/finance/search"
_HEADERS = {"User-Agent": "Mozilla/5.0"}


class YahooNewsRequestError(Exception):
    """Raised when the HTTP request to Yahoo Finance's search endpoint fails, times out, or errors server-side."""


def fetch_news_json(ticker: str, *, timeout: float = 10.0) -> dict:
    """Fetch the raw search-endpoint JSON payload (which includes a `news` array) for a ticker."""
    try:
        response = requests.get(
            YAHOO_SEARCH_URL,
            params={"q": ticker},
            headers=_HEADERS,
            timeout=timeout,
        )
    except requests.RequestException as exc:
        raise YahooNewsRequestError(f"Request for {ticker!r} failed: {exc}") from exc

    if response.status_code >= 500:
        raise YahooNewsRequestError(f"Request for {ticker!r} failed: server error {response.status_code}")

    try:
        return response.json()
    except ValueError as exc:
        raise YahooNewsRequestError(
            f"Request for {ticker!r} returned non-JSON response (status {response.status_code})"
        ) from exc
