# User Story: Local Dev Environment (Docker Compose)

**Cross-cutting infra** — not tied to one component; underpins Kafka, PostgreSQL, and Redis for every service that needs them locally.

```
As a MarketPulse developer
I want Kafka, PostgreSQL, and Redis running locally with one command
So that I can build and test the producers, Aggregation Service, and persistence
layer without installing and configuring each dependency by hand
```

## Revision note

The first implementation baked default values directly into `docker-compose.yml` (`${POSTGRES_USER:-marketpulse}`, etc.) so it would "just work" with zero flags. That was revised to externalize every property into `env/dev.env` — an explicitly dev-labeled, tracked properties file — with no inline fallbacks left in the compose file, so it's unambiguous which environment's config is in use and there's a clear, correctly-named place for a future `env/prod.env` (or equivalent) if MarketPulse ever needs one. The trade-off: `docker compose up -d` alone no longer works — `--env-file env/dev.env` is required. That's intentional (see acceptance criteria).

## Acceptance criteria

- Given Docker is installed, when `docker compose --env-file env/dev.env up -d` is run from the repo root, then Kafka, PostgreSQL, and Redis all start and each reports healthy.
- Given the stack is running, when a client connects on each service's default local port, then the connection succeeds (Kafka broker API, Postgres `SELECT 1`, Redis `PING`).
- Given the stack is stopped (`docker compose down`) and brought back up, when Postgres and Redis restart, then previously written data is still there — persistence must use named volumes, not ephemeral container storage.
- Given the services expose ports to the host, when bound, then they bind to the host configured in `env/dev.env` (`127.0.0.1` by default) only, not all interfaces — the default credentials are for local development and must not be reachable from the local network.
- Given `env/dev.env` is the single, explicitly-labeled source of dev config, when `docker-compose.yml` is read, then it contains no inline fallback values (no `${VAR:-default}`) — every property comes from that file, not from hidden defaults baked into the compose YAML.
- Given `docker compose` is run *without* `--env-file env/dev.env`, when it starts, then Docker Compose visibly warns that each variable is unset — this is intentional: config should be explicit, not silently substituted.

## Test coverage

| Acceptance criterion | Verification |
|---|---|
| All three services start and report healthy | Live run: `docker compose --env-file env/dev.env up -d` + `docker compose ps` showing `healthy` for all three |
| Each service accepts a real client connection | Live run: Kafka broker API versions query, `psql SELECT 1`, `redis-cli PING` — see [reference/local-dev.md](../reference/local-dev.md#verification) |
| Data survives a restart | Live run: write a row/key/topic, `docker compose down` (no `-v`), `docker compose --env-file env/dev.env up -d`, confirm it's still there |
| Ports bound to the configured host only | Inspected via `docker compose ps` port mapping (`127.0.0.1:5432->5432`, not `0.0.0.0:5432->5432`) |
| No inline fallback values in `docker-compose.yml` | Manual inspection — every `${VAR}` reference in the compose file is bare, no `:-default` |
| Missing `--env-file` warns rather than silently defaulting | Live run: `docker compose up -d` (no flag) produces `variable is not set` warnings for every property |

There's no `pytest` suite here — this is infra config, not application code, so "testing" is the live verification steps above (all run and confirmed working; see [reference/local-dev.md](../reference/local-dev.md)) rather than an automated test file.

## Status

Implemented and verified live — see [reference/local-dev.md](../reference/local-dev.md).
