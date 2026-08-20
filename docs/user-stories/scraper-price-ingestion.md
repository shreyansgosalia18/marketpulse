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
- Given a row with a null OHLCV value (Yahoo's gap-filling for non-trading days within the range), when parsed, then that row is skipped rather than producing bad data or an error.
- Given an empty watchlist, when `WatchlistScraper` is constructed, then construction is rejected with a clear error rather than allowed to silently do nothing later.

## Test coverage

| Acceptance criterion | Test(s) |
|---|---|
| Valid tickers return OHLCV bars | `test_parses_valid_payload_into_price_bars_in_row_order`, `test_returns_bars_for_a_ticker_with_valid_data` |
| No data at source → error result, not raised | `test_raises_no_data_error_when_chart_reports_an_error`, `test_raises_no_data_error_when_result_is_empty`, `test_raises_no_data_error_when_all_rows_are_null`, `test_unknown_ticker_yields_error_result_not_an_exception` |
| Source unreachable/timeout → error result | `test_network_failure_yields_error_result_not_an_exception` |
| One ticker's failure doesn't affect the batch | `test_one_failing_ticker_does_not_affect_the_others_in_the_batch` |
| Malformed response → clear error | `test_raises_value_error_when_quote_data_is_missing`, `test_raises_value_error_when_ohlcv_arrays_have_mismatched_length` |
| Null OHLCV rows are skipped | `test_skips_rows_with_null_ohlcv_values` |
| Empty watchlist rejected at construction | `test_empty_watchlist_is_rejected_at_construction` |

## Status

Implemented and tested — see [reference/scraper.md](../reference/scraper.md) for usage and [architecture/scraper.md](../architecture/scraper.md) for the internal design.
