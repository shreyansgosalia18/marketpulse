# Reference: Scraper Service (Price Ingestion)

**User story:** [scraper-price-ingestion](../user-stories/scraper-price-ingestion.md) · **Architecture:** [scraper internals](../architecture/scraper.md)

Implements the first slice of the **Scraper Service** component from the [root README](../../README.md#architecture): pulling daily price/volume history for a configurable watchlist of tickers.

> **Status:** price history fetch is implemented and tested. This slice does not yet publish to Kafka or persist anywhere — see [Known limitations](#known-limitations--not-yet-built).

## Features

- Configurable watchlist (any list of ticker symbols) passed into `WatchlistScraper`.
- Daily OHLCV (open/high/low/close/volume) history per ticker.
- HTTP I/O, response parsing, and orchestration are separate, independently testable modules.
- Per-ticker failure isolation — one bad ticker never fails the batch.
- `run_demo.py` CLI for manual, ad-hoc runs against the live API.

## Usage

```python
from marketpulse_scraper.watchlist import WatchlistScraper

scraper = WatchlistScraper(["AAPL", "MSFT"])
for result in scraper.fetch_history():
    if result.ok:
        print(result.ticker, len(result.bars), "bars")
    else:
        print(result.ticker, "failed:", result.error)
```

CLI, for manual/ad-hoc runs:

```
cd scraper
.venv\Scripts\python run_demo.py AAPL MSFT
```

## Testing

```
cd scraper
.venv\Scripts\python -m pytest tests/ -v
```

12 tests across `tests/test_parser.py` and `tests/test_watchlist.py`, covering the happy path and every sad path in the [user story's](../user-stories/scraper-price-ingestion.md) acceptance criteria. All HTTP calls are mocked — the suite makes no network requests.

## Known limitations / not yet built

- No Kafka producer — fetched data isn't published anywhere yet, just returned in-process.
- No persistence layer (PostgreSQL) — nothing is stored.
- No retry/backoff for transient failures, and no rate-limiting handling for large watchlists.
- Mutual funds aren't validated against this data source's symbol format.
- Yahoo Finance's chart endpoint is a public but *unofficial/undocumented* API — it can change or start blocking automated requests without notice (this is exactly what happened to the original data-source choice, stooq.com, mid-build — see git history). Worth revisiting with a stable/paid provider (e.g. Alpha Vantage, IEX Cloud) before this goes anywhere near production.

## Assumptions made

- Data source: Yahoo Finance's public chart endpoint (`query1.finance.yahoo.com`), chosen because it's keyless and needs no signup. Originally built against stooq.com's CSV endpoint, which turned out to have added JS-based anti-bot protection; switched sources rather than attempting to work around that protection.
- Ticker symbols follow Yahoo's convention (e.g. `AAPL`), not stooq's (e.g. `aapl.us`).
