# User Story: Local Dev Environment (Docker Compose)

**Cross-cutting infra** — not tied to one component; underpins Kafka, PostgreSQL, and Redis for every service that needs them locally.

```
As a MarketPulse developer
I want Kafka, PostgreSQL, and Redis running locally with one command
So that I can build and test the producers, Aggregation Service, and persistence
layer without installing and configuring each dependency by hand
```

## Acceptance criteria

- Given Docker is installed, when `docker compose up -d` is run from the repo root, then Kafka, PostgreSQL, and Redis all start and each reports healthy.
- Given the stack is running, when a client connects on each service's default local port, then the connection succeeds (Kafka broker API, Postgres `SELECT 1`, Redis `PING`).
- Given the stack is stopped (`docker compose down`) and brought back up, when Postgres and Redis restart, then previously written data is still there — persistence must use named volumes, not ephemeral container storage.
- Given the services expose ports to the host, when bound, then they bind to `127.0.0.1` only, not all interfaces — the default credentials are for local development and must not be reachable from the local network.
- Given no `.env` file is present, when the stack starts, then it still works using documented local-dev defaults (a missing `.env` should never be a hard failure for local dev).

## Test coverage

| Acceptance criterion | Verification |
|---|---|
| All three services start and report healthy | Live run: `docker compose up -d` + `docker compose ps` showing `healthy` for all three |
| Each service accepts a real client connection | Live run: Kafka broker API versions query, `psycopg2` `SELECT 1`, `redis-cli PING` — see [reference/local-dev.md](../reference/local-dev.md#verification) |
| Data survives a restart | Live run: write a row/key, `docker compose down` (no `-v`), `docker compose up -d`, confirm the row/key is still there |
| Ports bound to localhost only | Inspected via `docker compose ps` port mapping (`127.0.0.1:5432->5432`, not `0.0.0.0:5432->5432`) |
| Works with no `.env` present | Live run: stack started successfully with no `.env` file in the repo |

There's no `pytest` suite here — this is infra config, not application code, so "testing" is the live verification steps above (all run and confirmed working; see [reference/local-dev.md](../reference/local-dev.md)) rather than an automated test file.

## Status

Implemented and verified live — see [reference/local-dev.md](../reference/local-dev.md).
