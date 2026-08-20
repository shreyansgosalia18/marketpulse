# Reference: Local Dev Environment (Docker Compose)

**User story:** [local-dev-docker-compose](../user-stories/local-dev-docker-compose.md)

`docker-compose.yml` at the repo root brings up Kafka, PostgreSQL, and Redis for local development — everything the Aggregation Service, producers, and persistence layer will need to run against.

## Usage

```
docker compose up -d      # start all three services
docker compose ps         # check status — expect "healthy" for all three
docker compose down       # stop (data is preserved — named volumes aren't removed)
docker compose down -v    # stop AND wipe all data (fresh start)
```

| Service | Port (localhost only) | Default credentials |
|---|---|---|
| Kafka | `9092` | none (PLAINTEXT, local dev only) |
| PostgreSQL | `5432` | user `marketpulse`, password `devpassword`, db `marketpulse` |
| Redis | `6379` | none |

Override any Postgres credential by setting `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` (e.g. in a local `.env` file, already covered by `.gitignore`) before running `docker compose up -d`.

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
