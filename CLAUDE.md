# Nexus POC

## What This Is

A proof-of-concept implementation of the **Nexus unified financial transaction data contract**. This repo contains the runnable system — 4 Java microservices, a React UI, and Docker Compose orchestration — that demonstrates the full Nexus pipeline end-to-end.

The **Nexus schema definition and research** live in a separate repo (`Nexus`). This repo vendors a copy of the schema under `schema/` (see `schema/README.md` for provenance).

## System Overview

```
LE Simulator → [Kafka] → Nexus Transformer → [Kafka] → Rules Engine → [Kafka/PostgreSQL]
     ↑                         ↑                              ↑
     |                    nexus-api (BFF)                      |
     |                         ↑                              |
     +————————————— UI (React) ————————————————————————————————+
```

**Data pipeline:**
1. **LE Simulator** (port 8081) — Produces realistic Linking Engine `linkedtransaction` events to Kafka
2. **Nexus Transformer** (port 8082) — Consumes LE events, applies configurable engine (YAML + SpEL + named resolvers), produces validated Nexus transactions
3. **Rules Engine** (port 8080) — Consumes Nexus events, evaluates rules, produces double-entry ledger postings
4. **Nexus API** (port 8083) — BFF for the UI: engine config CRUD, test bench, DLQ, WebSocket aggregation
5. **UI** (Vite dev on port 5173) — React frontend: dashboard, config editor, test bench, live stream, DLQ viewer

**Infrastructure:** Kafka (Confluent 7.5), PostgreSQL 15, Zookeeper — all via Docker Compose.

## Quick Start

```bash
# Build all Java services
for svc in le-simulator nexus-transformer nexus-api rules-engine; do
  (cd $svc && ./gradlew build -x test)
done

# Start everything
docker compose up --build

# Start UI dev server
cd ui && npm install && npm run dev
```

## Repo Structure

```
├── CLAUDE.md              ← You are here
├── docker-compose.yml     ← Orchestrates all services + infra
├── schema/                ← Vendored Nexus schema (source of truth in Nexus repo)
├── docs/                  ← System-level architecture and design docs
│   ├── architecture.md    ← Component diagram, responsibilities, data flow
│   ├── data-flow.md       ← Kafka topics, message formats, worked examples
│   ├── engine-design.md   ← Configurable engine: YAML DSL, resolvers, state machines
│   └── deployment.md      ← Docker Compose setup, ports, AWS deployment path
├── le-simulator/          ← Spring Boot — LE event producer
├── nexus-transformer/     ← Spring Boot — LE→Nexus transformation engine
├── nexus-api/             ← Spring Boot — BFF for UI
├── rules-engine/          ← Spring Boot — Rule evaluation + ledger
└── ui/                    ← React + Vite + TypeScript frontend
```

Each service has its own `README.md` with: responsibility, API surface, configuration, and how to run standalone.

## Key Concepts

- **Nexus is NOT a ledger.** It produces financial transaction events. Downstream systems (like the rules engine) use these to maintain balances.
- **Product-agnostic language.** Always "Client Entity", never "Merchant". Always "CKO Entity", never "Checkout".
- **Configurable engine.** The transformer uses a YAML config + SpEL expressions + named resolvers. Business logic changes don't require code changes — only config or new resolvers.
- **Schema validation.** Every Nexus transaction is validated against `schema/nexus.schema.json` before publishing. Invalid events go to DLQ.
- **Incremental pillar arrival.** LE events arrive in versions as pillar data comes in (GW → COS → FIAPI → SD → Cash). Nexus handles partial state gracefully.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.x, Gradle |
| Messaging | Apache Kafka (Confluent 7.5) |
| Database | PostgreSQL 15 (Flyway migrations) |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, Monaco Editor, React Flow |
| Orchestration | Docker Compose |

## Conventions

- **Java package root:** `com.checkout.nexus.<module>` (e.g., `com.checkout.nexus.transformer`)
- **Config format:** YAML with SpEL expressions for the engine config
- **API style:** REST (JSON), WebSocket for live streaming
- **Testing:** JUnit 5 + Spring Boot Test for backend
- **Commits:** Conventional commits (`feat:`, `fix:`, `docs:`, `chore:`, `test:`)
