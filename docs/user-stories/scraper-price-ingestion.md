# User Story: Scraper Price Ingestion

**Component:** [Scraper Service](../reference/scraper.md) · **Architecture:** [scraper internals](../architecture/scraper.md)

```
As a MarketPulse operator
I want price/volume history pulled for each ticker in a configurable watchlist
So that downstream sentiment correlation has price data to work with
```

## Acceptance criteria

- Given a watchlist of valid tickers, when history is fetched, then each ticker returns a list of daily OHLCV bars.
- Given a ticker with no data at the source, when fetched, then that ticker reports an error result — it does not raise and does not appear as bars.
- Given the source is unreachable or times out, when fetched, then that ticker reports an error result.
- Given one ticker in the watchlist fails, when the batch is fetched, then the other tickers still return their data (no all-or-nothing failure).
- Given a malformed response, when parsed, then it fails with a clear error rather than silently returning wrong data.

## Status

Implemented and tested — see [reference/scraper.md](../reference/scraper.md) for usage and [architecture/scraper.md](../architecture/scraper.md) for the internal design.
