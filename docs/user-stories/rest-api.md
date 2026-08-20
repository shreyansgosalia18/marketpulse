# User Story: REST API

**Component:** Aggregation Service · **Architecture:** [aggregation service internals](../architecture/aggregation-service.md) · **Exposes:** [postgres-persistence-layer](postgres-persistence-layer.md), [redis-caching-layer](redis-caching-layer.md)

```
As a MarketPulse consumer (a future UI, or a person testing via Swagger)
I want to query a ticker's current trend summary and its price history
over HTTP
So that trend data is actually reachable from outside the JVM
```

## Design decisions

- **Two endpoints, matching the root README's already-documented "Planned API"** (`GET /api/v1/trends/{ticker}` and `GET /api/v1/trends/{ticker}/history`) — not inventing new API shape, just implementing what was already promised.
- **`/history` returns the raw price bar time series, not a series of historical trend summaries.** `TrendCalculator` only ever computes one current summary from the latest state; there's no windowed/historical trend computation in this codebase yet (see [aggregation-trend-computation](aggregation-trend-computation.md)'s scope decisions), so building a fake "trend history" endpoint would mean either inventing data or quietly recomputing the same current summary N times. Raw price history is real data that actually exists and is honestly what "history" means today. A historical-*trend* endpoint would be a real, separate future story once historical trend computation exists.
- **Dedicated response DTOs (`TrendSummaryResponse`, `PriceBarResponse`), not the domain records directly.** `TrendSummary` uses `Optional<Double>` fields, which is right for internal Java code but not idiomatic for a JSON contract (a public API shouldn't expose `Optional` semantics or change shape just because internal computation changes) — nullable fields on a purpose-built response type instead.
- **Unknown/no-data ticker → `404`, for both endpoints**, not `200` with an empty body. There's no independent "known tickers" registry in this system — a ticker either has data or it doesn't, so absence of data *is* absence of the resource from this API's point of view. Consistent behavior across both endpoints.
- **Swagger/OpenAPI UI via springdoc-openapi**, so the API is actually explorable/testable without writing a client — the explicit reason for this story right now (per the request that prompted it).
- **A structured JSON error body for unexpected failures**, not Spring's default whitelabel HTML error page — a `@RestControllerAdvice` maps uncaught exceptions to a consistent `{"error": "..."}` shape. "Not found" itself doesn't need this (handled directly via `ResponseEntity`); this is for genuinely unexpected failures.

## Acceptance criteria

- Given a ticker with existing trend data, when `GET /api/v1/trends/{ticker}` is called, then it returns `200` with the current trend summary (latest close, percent change if available, average sentiment if available, sentiment label counts).
- Given a ticker with no data, when `GET /api/v1/trends/{ticker}` is called, then it returns `404`, not `200` with a null/empty body.
- Given a ticker with existing price history, when `GET /api/v1/trends/{ticker}/history` is called, then it returns `200` with the full list of price bars, ordered by trade date.
- Given a ticker with no price history, when `GET /api/v1/trends/{ticker}/history` is called, then it returns `404`.
- Given the API is running, when `/swagger-ui.html` (or `/swagger-ui/index.html`) is requested, then the interactive Swagger UI loads and both endpoints are documented and callable from it.
- Given the API is running, when `/v3/api-docs` is requested, then it returns a valid OpenAPI 3 JSON document describing both endpoints.

## Explicitly out of scope

- No historical trend computation (a time series of summaries) — see design decisions above.
- No pagination on `/history` — every bar for the ticker comes back in one response. Fine at current data volumes; a real limitation once a ticker has years of history.
- No authentication/authorization — this is a local-dev API for now.

## Test coverage

| Acceptance criterion | Test(s) |
|---|---|
| Existing ticker → 200 with trend summary | `TrendControllerTest.getTrendSummaryReturnsOkWithSummaryBody` (mocked), `RestApiIntegrationTest.getTrendSummaryReturnsRealDataForATickerWithHistory` (live) |
| Unknown ticker → 404 (summary) | `TrendControllerTest.getTrendSummaryReturns404ForUnknownTicker` |
| Existing ticker → 200 with price history | `TrendControllerTest.getPriceHistoryReturnsOkWithBars` (mocked), `RestApiIntegrationTest.getPriceHistoryReturnsRealDataForATickerWithHistory` (live) |
| Unknown ticker → 404 (history) | `TrendControllerTest.getPriceHistoryReturns404ForUnknownTicker` |
| Swagger UI loads and documents both endpoints | `RestApiIntegrationTest.swaggerUiIsReachable` (live) |
| OpenAPI JSON document is valid and describes both endpoints | `RestApiIntegrationTest.openApiDocumentDescribesBothEndpoints` (live) |

## Status

Implemented and tested — see [reference/aggregation-service.md](../reference/aggregation-service.md).
