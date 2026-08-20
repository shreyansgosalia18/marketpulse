from datetime import datetime, timezone

from .models import PriceBar

_OHLCV_FIELDS = ("open", "high", "low", "close", "volume")


class NoDataError(Exception):
    """Raised when the source has no price history for the ticker."""


def parse_price_history(ticker: str, payload: dict) -> list[PriceBar]:
    """Parse a Yahoo Finance chart-endpoint JSON payload into PriceBar rows."""
    chart = payload.get("chart", {})

    error = chart.get("error")
    if error:
        description = error.get("description", str(error)) if isinstance(error, dict) else str(error)
        raise NoDataError(f"No price history available for {ticker!r}: {description}")

    results = chart.get("result")
    if not results:
        raise NoDataError(f"No price history available for {ticker!r}")

    result = results[0]
    timestamps = result.get("timestamp")
    quote_list = result.get("indicators", {}).get("quote")
    if not timestamps or not quote_list:
        raise ValueError(f"Unexpected response shape for {ticker!r}: missing timestamp/quote data")

    quote = quote_list[0]
    series = {field: quote.get(field) for field in _OHLCV_FIELDS}
    if any(values is None or len(values) != len(timestamps) for values in series.values()):
        raise ValueError(f"Unexpected response shape for {ticker!r}: OHLCV arrays missing or mismatched length")

    bars: list[PriceBar] = []
    for i, ts in enumerate(timestamps):
        row = {field: series[field][i] for field in _OHLCV_FIELDS}
        if any(value is None for value in row.values()):
            # Yahoo emits null OHLCV entries for non-trading gaps inside the range; skip them.
            continue
        bars.append(
            PriceBar(
                ticker=ticker,
                trade_date=datetime.fromtimestamp(ts, tz=timezone.utc).date(),
                open=float(row["open"]),
                high=float(row["high"]),
                low=float(row["low"]),
                close=float(row["close"]),
                volume=int(row["volume"]),
            )
        )

    if not bars:
        raise NoDataError(f"No usable price bars for {ticker!r}")

    return bars
