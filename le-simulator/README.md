# LE Simulator

Produces realistic Linking Engine `linkedtransaction` events to Kafka, simulating how the LE emits cumulative, versioned records as pillar data arrives.

## How It Works

The simulator has two modes:
- **Scenario Playback** — Plays one of 9 worked examples step-by-step. Each scenario emits multiple LE versions (e.g., v1 GW-only → v2 GW+COS → v3 all pillars), with configurable delay between pillar arrivals.
- **Random Generator** — Produces an indefinite stream of random LE events across all trade families until stopped.

Scenarios are loaded from `src/main/resources/` and map to the 9 examples in `schema/examples/`.

## API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/simulate/scenarios` | List all 9 available scenarios |
| `POST` | `/simulate/scenario/{id}` | Play scenario by ID (1-9), optional `?delay_ms=2000` |
| `POST` | `/simulate/random/start` | Start random generation, optional `?interval_ms=1000` |
| `POST` | `/simulate/random/stop` | Stop random generation |

## Kafka

- **Produces to:** `le.linked.transactions`
- **Message key:** `action_id`
- **Format:** JSON — full `LeLinkedTransaction` object

## Configuration

See `src/main/resources/application.yml`. Key properties:
- `spring.kafka.bootstrap-servers` — Kafka broker address
- `server.port` — HTTP port (default: 8081)

## Run Standalone

```bash
./gradlew bootRun
# Requires Kafka running on localhost:9092
```
