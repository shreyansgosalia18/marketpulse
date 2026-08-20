# MarketPulse

MarketPulse scrapes stock and mutual fund price data alongside financial news, aggregating sentiment signals with historical price trends to surface market movement summaries.

> **Status:** In active development. This README describes the target architecture; components are being built incrementally.

## What it does

- Pulls price/volume history for a configurable watchlist of stocks and mutual funds
- Scrapes financial news relevant to each ticker/fund
- Scores news sentiment (positive/negative/neutral) per ticker
- Correlates sentiment trends with price movement over time
- Exposes trend summaries via a REST API

## Architecture

| Component | Responsibility | Tech |
|---|---|---|
| [Scraper Service](docs/reference/scraper.md) | Pulls price data + financial news | Python |
| Sentiment Pipeline | Scores news sentiment per ticker | Python (NLP) |
| Event Stream | Decouples ingestion from processing | Kafka |
| Aggregation Service | Consumes events, computes trend summaries | Java / Spring Boot |
| Storage | Durable historical trend data | PostgreSQL |
| Cache | Fast lookups for frequently queried tickers | Redis |
| API | Exposes trend summaries | REST (Spring Boot) |

```
[Scraper (Python)] --> [Kafka: raw price + sentiment events]
                                    |
                                    v
                     [Aggregation Service (Spring Boot)]
                          |                    |
                          v                    v
                    [PostgreSQL]           [Redis cache]
                                                |
                                                v
                                        [REST API] --> consumers
```

## Planned API

```
GET /api/v1/trends/{ticker}          # current trend summary for a ticker/fund
GET /api/v1/trends/{ticker}/history  # historical trend data
```

## Documentation

Full docs — user stories, architecture diagrams, and per-component reference — live under [docs/](docs/README.md).

## Roadmap

- [x] Scraper service: price data ingestion — see [docs/reference/scraper.md](docs/reference/scraper.md) (fetch only; not yet wired to Kafka)
- [x] Scraper service: financial news ingestion — see [docs/reference/scraper.md](docs/reference/scraper.md) (fetch only; not yet wired to Kafka)
- [ ] Sentiment scoring pipeline
- [ ] Kafka event schema + producers
- [ ] Aggregation service consumer + trend computation
- [ ] PostgreSQL schema + persistence layer
- [ ] Redis caching layer
- [ ] REST API
- [ ] Docker Compose for local dev

## Local development

- **Scraper service**: see [docs/reference/scraper.md](docs/reference/scraper.md) for setup, usage, and testing.
- Other components: setup instructions will be added as they come online.

## License

MIT
