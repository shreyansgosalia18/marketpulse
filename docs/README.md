# MarketPulse Documentation

**Want the holistic picture — what's built vs. what's left?** Start at [architecture/system-overview.md](architecture/system-overview.md): one diagram plus a build-status table covering every component.

- **[user-stories/](user-stories/)** — requirements as testable user stories with acceptance criteria, one file per feature.
- **[architecture/](architecture/)** — diagrams: `system-overview.md` for the cross-component data flow and overall build status, one file per component for internal design.
- **[reference/](reference/)** — per-component docs: features, usage, testing, known limitations, assumptions.

## By component

### Scraper Service
- User stories: [user-stories/scraper-price-ingestion.md](user-stories/scraper-price-ingestion.md), [user-stories/scraper-news-ingestion.md](user-stories/scraper-news-ingestion.md), [user-stories/scraper-news-relevance-filtering.md](user-stories/scraper-news-relevance-filtering.md)
- Architecture: [architecture/scraper.md](architecture/scraper.md) (see also [architecture/system-overview.md](architecture/system-overview.md))
- Reference: [reference/scraper.md](reference/scraper.md)

### Local Dev Environment (Docker Compose)
- User story: [user-stories/local-dev-docker-compose.md](user-stories/local-dev-docker-compose.md)
- Reference: [reference/local-dev.md](reference/local-dev.md)
