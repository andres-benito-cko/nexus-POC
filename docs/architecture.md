# Nexus POC — System Architecture

## System Diagram

```
┌─────────────────────────────────────────────────┐
│                  nexus-ui (React)                │
│  Dashboard | Config | Test Bench | Live | DLQ    │
└──────────────────┬──────────────────────────────┘
                   │ REST + WebSocket
                   ▼
┌─────────────────────────────────────────────────┐
│           nexus-api  (port 8083)                 │
│  Config API | Resolver Registry | DLQ API        │
│  WebSocket aggregator | Transaction proxy        │
│  Postgres (Flyway) ← engine_configs table        │
└──────┬────────────────┬───────────────┬──────────┘
       │                │               │
       ▼                ▼               ▼
  nexus-transformer  rules-engine   le-simulator
  (8082)             (8080)         (8081)
  Kafka consumer     Postgres       Kafka producer
  Config consumer    Rules CRUD     Scenario playback
```

## Data Flow Summary

```
LE Simulator --> [le.linked.transactions] --> Nexus Transformer --> [nexus.transactions] --> Rules Engine --> [nexus.ledger.entries]
                                                                                                  |
                                                                                            PostgreSQL
                                                                                                  ^
UI <---- WebSocket (live events from all 3 topics) + REST (rules CRUD, tx history) ---- Rules Engine (BFF)
```

## Component Summary

| Component | Port | Responsibility |
|---|---|---|
| le-simulator | 8081 | Produces realistic LE `linkedtransaction` events to Kafka; supports scenario playback via REST |
| nexus-transformer | 8082 | Consumes LE events, applies configurable engine (YAML + SpEL + named resolvers), produces validated Nexus transactions |
| rules-engine | 8080 | Consumes Nexus events, evaluates rules, produces double-entry ledger postings; acts as BFF for WebSocket and rules CRUD |
| nexus-api | 8083 | BFF for the UI: engine config CRUD, resolver registry, DLQ, WebSocket aggregation, transaction proxy |
| nexus-ui | 5173 (dev) | React frontend: dashboard, config editor, test bench, live event stream, DLQ viewer |

## Tech Stack

| Component | Technology | Rationale |
|---|---|---|
| All backend services | Java 17, Spring Boot 3.x, Gradle | Team standard; strong Kafka/Postgres ecosystem |
| Messaging | Apache Kafka (Confluent 7.5) | Durable, ordered event stream; mirrors production target architecture |
| Database | PostgreSQL 15 with Flyway | Reliable relational store; Flyway keeps schema versioned and reproducible |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS | Fast iteration; Monaco Editor for YAML config; React Flow for pipeline diagrams |
| Orchestration | Docker Compose | Single-command local startup; all infra and services in one file |
| Expression language | Spring Expression Language (SpEL) | Already on classpath; concise for field-mapping expressions in YAML |

## Scope

### What This POC Proves

- The Nexus schema is implementable — the `Header → Trades → Legs → Fees` structure works in practice across all 9 scenario types.
- The LE-to-Nexus mapping is complete and correct — every Linking Engine scenario type produces a valid, schema-conformant Nexus transaction.
- Incremental pillar arrival is handled gracefully — partial LE events transition through `NOT_LIVE → LIVE → DEAD` without data loss or schema violations.
- Downstream integration is straightforward — the rules engine needs no pillar-specific logic; it consumes a single, uniform Nexus topic.
- Real-time observability is achievable — stakeholders can watch the full pipeline live via the UI dashboard and live stream view.
- The contract simplifies integration — one schema, one topic replaces N pillar-specific event structures and consumers.

### What This POC Defers

| Deferred Item | Rationale |
|---|---|
| Authentication / authorization | Not needed for local demo |
| Multi-tenancy | POC uses single client entity |
| Schema Registry (Avro/Protobuf) | JSON sufficient for POC |
| Monitoring (Grafana/Prometheus) | Not needed to prove data model |
| Production Kafka config | Partitioning, exactly-once semantics |
| AWS deployment scripts | Architecture AWS-ready; Terraform/CDK deferred |
| Idempotency / exactly-once | POC uses at-least-once |
| Performance testing | POC validates correctness, not throughput |
