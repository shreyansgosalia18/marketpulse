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
| [Sentiment Pipeline](docs/reference/sentiment-pipeline.md) | Scores news sentiment per ticker | Python (NLP) |
| [Event Stream](docs/reference/event-stream.md) | Decouples ingestion from processing | Kafka |
| [Aggregation Service](docs/reference/aggregation-service.md) | Consumes events, computes trend summaries | Java / Spring Boot |
| [Storage](docs/reference/aggregation-service.md) | Durable historical trend data | PostgreSQL |
| [Cache](docs/reference/aggregation-service.md) | Fast lookups for frequently queried tickers | Redis |
| [API](docs/reference/aggregation-service.md) | Exposes trend summaries | REST (Spring Boot) |

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

This shows the target architecture. For what's actually built vs. still planned right now, see the canonical status diagram at [docs/architecture/system-overview.md](docs/architecture/system-overview.md).

## API

```
GET /api/v1/trends/{ticker}          # current trend summary for a ticker/fund
GET /api/v1/trends/{ticker}/history  # historical price data
```

Interactive Swagger UI: `http://localhost:8080/swagger-ui/index.html` (once the Aggregation Service is running — see [docs/reference/aggregation-service.md](docs/reference/aggregation-service.md)).

## Documentation

Full docs — user stories, architecture diagrams, and per-component reference — live under [docs/](docs/README.md).

## Roadmap

- [x] Scraper service: price data ingestion — see [docs/reference/scraper.md](docs/reference/scraper.md)
- [x] Scraper service: financial news ingestion — see [docs/reference/scraper.md](docs/reference/scraper.md)
- [x] Sentiment scoring pipeline — see [docs/reference/sentiment-pipeline.md](docs/reference/sentiment-pipeline.md)
- [x] Kafka event schema + producers — see [docs/reference/event-stream.md](docs/reference/event-stream.md)
- [x] Aggregation service consumer + trend computation — see [docs/reference/aggregation-service.md](docs/reference/aggregation-service.md)
- [x] PostgreSQL schema + persistence layer — see [docs/reference/aggregation-service.md](docs/reference/aggregation-service.md) (verified surviving a real process restart)
- [x] Redis caching layer — see [docs/reference/aggregation-service.md](docs/reference/aggregation-service.md) (verified degrading gracefully with Redis stopped outright)
- [x] REST API — see [docs/reference/aggregation-service.md](docs/reference/aggregation-service.md) (Swagger UI at `/swagger-ui/index.html`)
- [x] Docker Compose for local dev — see [docs/reference/local-dev.md](docs/reference/local-dev.md)

**All roadmap items are now done.** What's next isn't on this list yet — most likely a UI to actually consume the API. Nothing decided yet; see [docs/architecture/system-overview.md](docs/architecture/system-overview.md) for the current status diagram.

## Local development

- **Kafka, PostgreSQL, Redis**: `docker compose --env-file env/dev.env up -d` — see [docs/reference/local-dev.md](docs/reference/local-dev.md) for details, ports, and credentials.
- **Scraper service**: see [docs/reference/scraper.md](docs/reference/scraper.md) for setup, usage, and testing.
- **Publishing to Kafka**: see [docs/reference/event-stream.md](docs/reference/event-stream.md) for topics, schema, and usage.
- **Sentiment pipeline**: see [docs/reference/sentiment-pipeline.md](docs/reference/sentiment-pipeline.md) for setup, usage, and testing.
- **Aggregation service + REST API** (Java 21, no system-wide Maven needed — bundles `./mvnw`): see [docs/reference/aggregation-service.md](docs/reference/aggregation-service.md) for setup, usage, testing, and the Swagger UI URL.

## License

MIT
