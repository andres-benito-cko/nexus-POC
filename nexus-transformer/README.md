# Nexus Transformer

Consumes Linking Engine events, applies a configurable transformation engine, and produces validated Nexus transaction events. This is the core of the Nexus pipeline.

## How It Works

The transformer uses a **config-driven engine** with three layers:

1. **YAML config** (`nexus-engine-config.yaml`) — Classification rules, state machines, trade/leg assembly rules, field mappings
2. **SpEL expressions** — Conditions (`$when`), field resolution (`$field`), fallbacks (`$fallback`)
3. **Named resolvers** — Java components for complex domain logic (`$resolve: SETTLEMENT_AMOUNT`)

### Engine Pipeline

```
LeContext → Classifier → StateMachineRunner → TradeAssembler → LegAssembler → BlockAssembler → NexusValidator
```

Each step is a focused, testable unit. See `docs/engine-design.md` for the full design.

### Built-in Resolvers

| Resolver | Purpose |
|----------|---------|
| `SETTLEMENT_AMOUNT` | SD vs COS amount extraction + currency normalisation |
| `SCHEME_FEES` | SD (ACTUAL) vs COS (PREDICTED) fee arrays, type mapping |
| `FUNDING_AMOUNT` | FIAPI holding amount extraction |
| `ROLLING_RESERVE_AMOUNT` | FIAPI rolling reserve extraction + zero-guard |
| `FUNDING_FEES` | FIAPI fee actions mapped to Nexus fee types |
| `GATEWAY_AMOUNT` | Gateway event amount extraction |
| `CASH_AMOUNT` | Cash event amount extraction |

## API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/blocks/{id}` | Get a Nexus transaction by ID |
| `GET` | `/transactions?limit=N` | List recent Nexus transactions |

## Kafka

- **Consumes from:** `le.linked.transactions`
- **Produces to:** `nexus.blocks` (valid) and `nexus.blocks.dlq` (invalid)
- **Message key:** `nexus_id` (= `action_id`)

## Configuration

- `src/main/resources/application.yml` — Spring Boot config
- `src/main/resources/nexus-engine-config.yaml` — Engine rules (classification, state machines, trades, field mappings, fee mappings)
- `src/main/resources/nexus.schema.json` — JSON Schema for output validation

## Key Packages

```
com.checkout.nexus.transformer
├── engine/
│   ├── config/       # YAML config POJOs
│   ├── context/      # LeContext — pillar wrapper with named access
│   ├── expression/   # ExpressionEvaluator (SpEL-based)
│   ├── resolver/     # FieldResolver interface + built-in resolvers
│   ├── pipeline/     # Classifier, StateMachineRunner, assemblers
│   └── NexusEngine   # Entry point: LeContext → NexusBlock
├── validation/       # NexusValidator + DlqHandler
├── model/            # NexusBlock, Trade, Leg, Fee POJOs
│   └── le/           # LE input model (LeLinkedTransaction, pillar events)
└── service/          # TransformerService (Kafka wiring)
```

## Run Standalone

```bash
./gradlew bootRun
# Requires Kafka on localhost:9092 and nexus-api on localhost:8083
```

## Tests

```bash
./gradlew test
# Runs: engine config loading, expression evaluation, classifier,
# leg assembler, state machine, resolvers, validator — all unit tests
```
