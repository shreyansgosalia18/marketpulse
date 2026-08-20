# Reference: Scraper Service

**Architecture:** [scraper internals](../architecture/scraper.md) · **User stories:** [price ingestion](../user-stories/scraper-price-ingestion.md), [news ingestion](../user-stories/scraper-news-ingestion.md), [news relevance filtering](../user-stories/scraper-news-relevance-filtering.md)

Implements the **Scraper Service** component from the [root README](../../README.md#architecture): pulling daily price/volume history and financial news for a configurable watchlist of tickers.

> **Status:** price and news fetch are both implemented and tested. Neither is wired to Kafka or persisted yet — see [Known limitations](#known-limitations--not-yet-built).

## Price ingestion

**User story:** [scraper-price-ingestion](../user-stories/scraper-price-ingestion.md)

### Features

- Configurable watchlist (any list of ticker symbols) passed into `WatchlistScraper`.
- Daily OHLCV (open/high/low/close/volume) history per ticker.
- HTTP I/O, response parsing, and orchestration are separate, independently testable modules.
- Per-ticker failure isolation — one bad ticker never fails the batch.
- `run_demo.py` CLI for manual, ad-hoc runs against the live API.

### Usage

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

## News ingestion

**User story:** [scraper-news-ingestion](../user-stories/scraper-news-ingestion.md)

### Features

- Same configurable-watchlist shape as price ingestion, via `NewsScraper`.
- Per-ticker list of news articles (title, publisher, link, published time, stable `uuid`).
- An empty article list is treated as a normal, successful result — not every ticker has news at a given moment, and that's not an error.
- A single malformed article is skipped without failing the rest of that ticker's results; a malformed response *shape* still raises.
- **Relevance filtering** ([story](../user-stories/scraper-news-relevance-filtering.md)): articles are kept only if their title mentions the ticker symbol or the resolved company name (whole-word, case-insensitive; tolerant of corporate suffixes like "Inc."). An unresolvable ticker (no matching quote in the source response) yields an empty list instead of the source's generic/trending fallback.
- `run_news_demo.py` CLI for manual, ad-hoc runs against the live API.

### Usage

```python
from marketpulse_scraper.news_scraper import NewsScraper

scraper = NewsScraper(["AAPL", "MSFT"])
for result in scraper.fetch_news():
    if result.ok:
        print(result.ticker, len(result.articles), "articles")
    else:
        print(result.ticker, "failed:", result.error)
```

CLI, for manual/ad-hoc runs:

```
cd scraper
.venv\Scripts\python run_news_demo.py AAPL MSFT
```

## Testing

```
cd scraper
.venv\Scripts\python -m pytest tests/ -v
```

34 tests across `tests/test_parser.py`, `tests/test_watchlist.py`, `tests/test_news_parser.py`, `tests/test_news_scraper.py`, and `tests/test_relevance.py`, covering the happy path and every sad path in all three user stories' acceptance criteria. Each story's doc includes a test-coverage table mapping every acceptance criterion to its test(s). All HTTP calls are mocked — the suite makes no network requests.

## Known limitations / not yet built

- No Kafka producer — fetched data (price or news) isn't published anywhere yet, just returned in-process.
- No persistence layer (PostgreSQL) — nothing is stored.
- No retry/backoff for transient failures, and no rate-limiting handling for large watchlists.
- Mutual funds aren't validated against either data source's symbol format.
- Both data sources are public but *unofficial/undocumented* Yahoo Finance APIs — they can change or start blocking automated requests without notice (this is exactly what happened to the original price data-source choice, stooq.com, mid-build — see git history). Worth revisiting with a stable/paid provider (e.g. Alpha Vantage, IEX Cloud for price; a licensed news API for news) before this goes anywhere near production.
- **News relevance filtering is a title keyword match, not semantic relevance** ([story](../user-stories/scraper-news-relevance-filtering.md)). It reliably rejects the generic/trending noise Yahoo's search endpoint returns for unrecognized queries (verified live), but it can still miss a genuinely relevant article whose title doesn't happen to say the company name, and the corporate-suffix list it strips (Inc., Corp., Ltd., ...) isn't exhaustive. Revisit if/when sentiment scoring needs stricter precision.

## Assumptions made

- Price data source: Yahoo Finance's public chart endpoint (`query1.finance.yahoo.com`), chosen because it's keyless and needs no signup. Originally built against stooq.com's CSV endpoint, which turned out to have added JS-based anti-bot protection; switched sources rather than attempting to work around that protection.
- News data source: Yahoo Finance's public search endpoint (`query2.finance.yahoo.com`), same domain/family as the price source, also keyless. Deliberately not Google News RSS — its terms restrict use to "personal feed reader, non-commercial use," which a backend service doesn't qualify as.
- Ticker symbols follow Yahoo's convention (e.g. `AAPL`), not stooq's (e.g. `aapl.us`).
- Relevance filtering resolves the company name from the same search response's `quotes` array (no extra request needed) and matches it against article titles only — the source doesn't reliably provide full article body text to match against.
