# Reference: Local Dev Environment (Docker Compose)

**User story:** [local-dev-docker-compose](../user-stories/local-dev-docker-compose.md)

`docker-compose.yml` at the repo root brings up Kafka, PostgreSQL, and Redis for local development — everything the Aggregation Service, producers, and persistence layer will need to run against. All environment-specific values (ports, host binding, Postgres credentials) live in [env/dev.env](../../env/dev.env), not inline in the compose file — `docker-compose.yml` has no fallback defaults, so the env file is required.

## Environments

Only **dev** exists today — `env/dev.env`, tracked in git, safe to commit (every value is a public local-dev default, not a secret). There is no prod environment yet, and when one exists, it won't reuse this compose file: real Kafka/Postgres/Redis in production means managed services or a proper orchestrated deployment, not `docker compose` on a laptop. `env/dev.env`'s header comment documents this explicitly so it isn't mistaken for a template that just needs prod values swapped in.

## Usage

```
docker compose --env-file env/dev.env up -d      # start all three services
docker compose ps                                 # check status — expect "healthy" for all three
docker compose down                                # stop (data is preserved — named volumes aren't removed)
docker compose down -v                             # stop AND wipe all data (fresh start)
```

`--env-file env/dev.env` is required — running `docker compose up -d` without it produces `variable is not set` warnings for every property (Docker Compose falls back to blank strings, which breaks Postgres auth and binds ports to an empty host). That's a deliberate trade-off: explicit config over silent defaults.

| Service | Port (localhost only, from `env/dev.env`) | Default credentials |
|---|---|---|
| Kafka | `9092` | none (PLAINTEXT, local dev only) |
| PostgreSQL | `5432` | user `marketpulse`, password `devpassword`, db `marketpulse` |
| Redis | `6379` | none |

To change a port or credential, edit `env/dev.env` directly (it's tracked, not a gitignored template) or copy it to a local, gitignored variant and point `--env-file` at that instead.

## Verification

These are the checks run live to confirm the stack actually works, not just that containers start:

```
# Kafka — real broker request, not just "container is up"
docker exec marketpulse-kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092

# Postgres — real client query
docker exec marketpulse-postgres psql -U marketpulse -d marketpulse -c "SELECT 1;"

# Redis — real client command
docker exec marketpulse-redis redis-cli PING
```

Persistence (data survives `docker compose down` + `up`, without `-v`) was verified for all three: a Postgres row, a Redis key, and a Kafka topic all still existed after a full container recreation.

## Known limitations / not yet built

- **Local dev only.** Default credentials are intentionally weak/public in this repo; ports are bound to `127.0.0.1` specifically so this is never reachable from the local network, but this compose file must not be used as-is for anything beyond a developer's own machine.
- Single-node/single-broker for all three services — no replication, no clustering. Fine for development, not representative of production topology.
- No topic/schema provisioning yet — Kafka comes up empty; topics get created once the [Kafka event schema + producers](../../README.md#roadmap) roadmap item is built.
- No seed data for Postgres — the schema itself doesn't exist yet either (separate roadmap item).

## Assumptions made

- **Kafka image**: `apache/kafka:3.8.0` in KRaft combined mode (broker + controller in one container, no separate Zookeeper) — simpler for local dev than the classic two-container Zookeeper setup, and it's Apache's own official image.
- **Kafka's `log.dirs` had to be set explicitly.** The image's default `log.dirs` is `/tmp/kafka-logs`, not the `/var/lib/kafka/data` path that looked like the obvious place to mount a volume. The first version of this compose file mounted a volume at `/var/lib/kafka/data` while Kafka kept writing to `/tmp/kafka-logs` underneath — health checks passed and the broker worked fine, but every topic silently vanished on `docker compose down` because nothing was actually being persisted. Caught by explicitly testing a restart (create topic → `down` → `up` → check topic still exists) rather than trusting that "healthy" meant "persistent." Fixed via `KAFKA_LOG_DIRS: /var/lib/kafka/data` in the compose file, then re-verified.
- Postgres/Redis images (`postgres:16-alpine`, `redis:7-alpine`) chosen for small image size and because their default data directories (`/var/lib/postgresql/data`, `/data`) are well-documented and match what was mounted — verified live, no surprises there.
- **Config was moved from inline compose defaults to `env/dev.env`** on request, once it was pointed out that "dev" should be an explicit, correctly-labeled environment rather than magic values baked into `docker-compose.yml`. No prod file was added alongside it — there's no prod deployment target yet, and prod Kafka/Postgres/Redis won't be a variant of this dev-only compose file when one exists. Revalidated live after the change: fresh `down -v` + `up --env-file env/dev.env`, all three healthy, ports/credentials confirmed resolved correctly, real client connections still succeed.
