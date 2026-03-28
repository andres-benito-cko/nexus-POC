# Nexus API

Backend-for-Frontend (BFF) service for the Nexus UI. Provides engine config management, test bench, DLQ viewer, and WebSocket event streaming.

## How It Works

This service is the UI's single backend. It:
- Stores and versions engine configs in PostgreSQL
- Proxies transaction queries to the transformer
- Exposes a test bench endpoint (run an LE event through the engine without Kafka)
- Streams live events from Kafka topics over WebSocket
- Provides DLQ event listing and replay

## API

### Config Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/configs` | List all engine configs |
| `POST` | `/configs` | Create a new config version |
| `PUT` | `/configs/{id}` | Update a config |
| `POST` | `/configs/{id}/activate` | Set as active config |
| `POST` | `/configs/validate` | Dry-run validation |

### Test Bench

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/test-bench` | Transform an LE event → Nexus output (no Kafka) |

### DLQ

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/dlq` | List DLQ events |
| `POST` | `/dlq/{id}/replay` | Replay a DLQ event |

### Resolvers

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/resolvers` | List registered resolvers with params |

### WebSocket

| Endpoint | Description |
|----------|-------------|
| `/ws` | Live event stream (LE + Nexus + Ledger events) |

## Database

PostgreSQL with Flyway migrations in `src/main/resources/db/migration/`.

Key table: `engine_configs` — stores versioned YAML configs as JSONB with `is_active` flag.

## Configuration

- `src/main/resources/application.yml` — Spring Boot + Kafka + datasource config
- `server.port` — default: 8083
- `nexus.transformer.url` — URL of the transformer service for proxied queries

## Run Standalone

```bash
./gradlew bootRun
# Requires Kafka on localhost:9092 and PostgreSQL on localhost:5432
```
