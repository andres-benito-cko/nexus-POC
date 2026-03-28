# Rules Engine

Consumes Nexus transaction events, evaluates configurable rules, and produces double-entry ledger postings. Also acts as the WebSocket hub for live event streaming to the UI.

## How It Works

1. Receives every Nexus transaction event from Kafka
2. Matches each event against rule conditions (product_type, transaction_type, transaction_status, leg_type, party_type)
3. For each matched rule, generates debit/credit ledger entries
4. Persists entries to PostgreSQL and publishes to Kafka
5. Fans out all events (LE + Nexus + Ledger) to WebSocket clients

## API

### Rules CRUD

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/rules` | List all rules |
| `POST` | `/rules` | Create a rule |
| `GET` | `/rules/{id}` | Get rule by ID |
| `PUT` | `/rules/{id}` | Update a rule |
| `DELETE` | `/rules/{id}` | Delete a rule |

### Ledger

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/ledger/entries?nexusId={id}&limit=N` | Query ledger entries |
| `GET` | `/ledger/entries/summary` | Ledger summary |

### Transactions

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/transactions?limit=N` | List Nexus transactions |
| `GET` | `/blocks/{id}` | Get transaction |
| `GET` | `/blocks/{id}/ledger` | Ledger entries for a transaction |

## Kafka

- **Consumes from:** `nexus.blocks`
- **Produces to:** `nexus.ledger.entries`

## Database

PostgreSQL with Flyway migrations. Tables: `rules`, `ledger_entries`, `nexus_blocks`.

Seed rules in `src/main/resources/db/migration/V3__seed_rules.sql`.

## Configuration

- `src/main/resources/application.yml` — Spring Boot + Kafka + datasource
- `server.port` — default: 8080

## Run Standalone

```bash
./gradlew bootRun
# Requires Kafka on localhost:9092 and PostgreSQL on localhost:5432
```
