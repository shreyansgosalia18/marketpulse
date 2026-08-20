# Architecture: Scraper Service

**Fits into:** [system overview](system-overview.md) · **Reference:** [scraper reference doc](../reference/scraper.md)

## Internal module design

```mermaid
flowchart TD
    CLI[run_demo.py] --> WLS[WatchlistScraper]
    WLS -->|"per ticker"| YC[yahoo_client.fetch_history_json]
    YC -->|raw JSON| P[parser.parse_price_history]
    P -->|"list[PriceBar]"| WLS
    WLS -->|"list[ScrapeResult]"| CLI
    YC -. raises .-> ERR1[YahooRequestError]
    P -. raises .-> ERR2[NoDataError / ValueError]
    ERR1 --> WLS
    ERR2 --> WLS
    WLS -->|caught & wrapped as| RESULT["ScrapeResult(ok=False, error=...)"]
```

`WatchlistScraper` catches every failure mode from the client and parser and turns it into a `ScrapeResult` for that ticker, so a single ticker's problem never propagates as an exception out of `fetch_history()`.

| Module | Responsibility |
|---|---|
| `models.py` | `PriceBar` — one day of OHLCV data |
| `yahoo_client.py` | HTTP I/O against Yahoo Finance's public chart endpoint |
| `parser.py` | Turns the raw JSON payload into `PriceBar` rows; detects "no data" vs. malformed data |
| `watchlist.py` | `WatchlistScraper` — orchestrates the batch, isolates per-ticker failures into `ScrapeResult` |
