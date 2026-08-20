# User Story: Scraper News Relevance Filtering

**Component:** [Scraper Service](../reference/scraper.md) · **Architecture:** [scraper internals](../architecture/scraper.md) · **Follows on from:** [scraper-news-ingestion](scraper-news-ingestion.md)

```
As a MarketPulse operator
I want fetched news articles filtered to ones actually relevant to the queried ticker
So that sentiment scoring isn't fed noise from Yahoo's generic/trending search fallback
```

## Background

Verified live (see [reference/scraper.md](../reference/scraper.md#known-limitations--not-yet-built)): querying a deliberately bogus ticker still returned 8 unrelated "trending" articles instead of an empty list. Yahoo's search endpoint doesn't validate the query is a real, relevant ticker — it always returns *something*. Without filtering, articles get attributed to tickers they have nothing to do with.

## Acceptance criteria

- Given a valid ticker whose company name can be resolved from the source response, when news is fetched, then only articles whose title mentions the ticker symbol or the company name (case-insensitive, whole-word match) are returned.
- Given a company name with a common corporate suffix (Inc., Corp., Corporation, Co., Ltd., PLC, Group, Holdings), when matching, then the suffix is not required — "Apple" alone must match a title even though the resolved name is "Apple Inc.".
- Given an article title that doesn't mention the ticker or the company name, when filtering runs, then that article is excluded from the result.
- Given a query that resolves no matching company (e.g. an unknown/bogus symbol), when news is fetched, then the result is an empty article list rather than the source's generic/trending fallback.
- Given filtering removes every raw article for a ticker, when fetched, then the result is still a success (`ok=True`) with an empty list — filtering to zero is not an error, consistent with [scraper-news-ingestion](scraper-news-ingestion.md)'s "no news is not an error" rule.

## Explicitly out of scope

- No NLP/semantic relevance scoring — this is a title keyword match, not a claim of true topical relevance. Good enough to reject obviously-unrelated trending noise; not a substitute for a real relevance model if sentiment scoring later needs one.
- No filtering on article body text, only the title (the source doesn't reliably provide full body text).

## Test coverage

| Acceptance criterion | Test(s) |
|---|---|
| Title mentions ticker or company name → kept | `test_keeps_article_mentioning_ticker_symbol`, `test_keeps_article_mentioning_company_name` |
| Corporate suffix not required to match | `test_matches_company_name_without_corporate_suffix` |
| Title mentions neither → excluded | `test_excludes_article_not_mentioning_ticker_or_company` |
| Unresolved company name → empty result, not fallback noise | `test_unresolvable_ticker_yields_empty_articles_not_error` |
| Filtered-to-zero is still a success | `test_filtering_to_zero_articles_is_still_ok` |

## Status

Implemented and tested — see [reference/scraper.md](../reference/scraper.md) for usage and [architecture/scraper.md](../architecture/scraper.md) for the internal design.
