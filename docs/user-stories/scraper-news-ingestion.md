# User Story: Scraper News Ingestion

**Component:** [Scraper Service](../reference/scraper.md) · **Architecture:** [scraper internals](../architecture/scraper.md)

```
As a MarketPulse operator
I want financial news relevant to each ticker/fund in my watchlist scraped
So that sentiment scoring has news text to work with per ticker
```

## Acceptance criteria

- Given a watchlist of valid tickers, when news is fetched, then each ticker returns a list of news articles (title, publisher, link, published time, and a stable id).
- Given a ticker with no news currently available, when fetched, then that ticker returns an **empty list, not an error** — absence of news is a normal, expected outcome (unlike absent price history, which is a sad path).
- Given the source is unreachable or times out, when fetched, then that ticker reports an error result.
- Given one ticker in the watchlist fails, when the batch is fetched, then the other tickers still return their data (no all-or-nothing failure).
- Given a response with an unexpected shape (missing/wrong-typed news field), when parsed, then it fails with a clear error rather than silently returning wrong data.
- Given one malformed article within an otherwise valid response, when parsed, then that single article is skipped and the rest of the ticker's articles are still returned — a bad article shouldn't cost the whole ticker.
- Given an empty watchlist, when `NewsScraper` is constructed, then construction is rejected with a clear error rather than allowed to silently do nothing later.

## Test coverage

| Acceptance criterion | Test(s) |
|---|---|
| Valid tickers return articles (title, publisher, link, time, id) | `test_parses_valid_payload_into_news_articles_in_order`, `test_returns_articles_for_a_ticker_with_news` |
| No news → empty list, not an error | `test_empty_news_list_returns_empty_list_not_an_error`, `test_ticker_with_no_news_is_ok_with_empty_article_list` |
| Source unreachable/timeout → error result | `test_network_failure_yields_error_result_not_an_exception` |
| One ticker's failure doesn't affect the batch | `test_one_failing_ticker_does_not_affect_the_others_in_the_batch` |
| Unexpected response shape → clear error | `test_raises_value_error_when_news_field_is_missing`, `test_raises_value_error_when_news_field_is_not_a_list`, `test_malformed_response_shape_yields_error_result_not_an_exception` |
| Malformed article skipped, rest still returned | `test_skips_malformed_articles_but_keeps_valid_ones` |
| Empty watchlist rejected at construction | `test_empty_watchlist_is_rejected_at_construction` |

## Status

Implemented and tested — see [reference/scraper.md](../reference/scraper.md) for usage and [architecture/scraper.md](../architecture/scraper.md) for the internal design.

**Note:** `NewsScraper.fetch_news()`'s output is additionally relevance-filtered before it's returned — see [scraper-news-relevance-filtering](scraper-news-relevance-filtering.md). The acceptance criteria above describe ingestion in isolation; the article list a caller actually receives has already had obviously-irrelevant results removed.
