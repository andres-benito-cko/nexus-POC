# Nexus POC — Data Flow

## Kafka Topics

| Topic | Producer | Consumer | Format | Key | Partitions |
|---|---|---|---|---|---|
| `le.linked.transactions` | LE Simulator | Nexus Transformer | JSON | action_id | 3 |
| `nexus.blocks` | Nexus Transformer | Rules Engine, Event Gateway | JSON | action_id | 3 |
| `nexus.ledger.entries` | Rules Engine | Event Gateway | JSON | nexus_id | 3 |

**Config notes:**
- Replication factor: 1 (local POC; increase for production)
- Consumer groups: `nexus-transformer-group`, `rules-engine-group`, `event-gateway-group`

## Database Schema

All three tables live in the shared `nexus` PostgreSQL database. The rules engine owns `rules` and `ledger_entries`; nexus-api owns `nexus_blocks`.

### rules table

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | VARCHAR(64) | PRIMARY KEY | Rule identifier |
| name | VARCHAR(255) | NOT NULL | Human-readable rule name |
| condition | JSONB | NOT NULL | Match criteria |
| entries | JSONB | NOT NULL | Array of entry templates |
| enabled | BOOLEAN | NOT NULL DEFAULT TRUE | Whether active |
| created_at | TIMESTAMP | NOT NULL DEFAULT NOW() | Creation timestamp |
| updated_at | TIMESTAMP | NOT NULL DEFAULT NOW() | Last update |

### ledger_entries table

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | VARCHAR(64) | PRIMARY KEY | Entry identifier |
| nexus_id | VARCHAR(64) | NOT NULL, INDEX | Nexus transaction ID |
| nexus_id | VARCHAR(64) | NOT NULL | Trade ID |
| leg_id | VARCHAR(64) | NOT NULL | Leg ID |
| rule_id | VARCHAR(64) | NOT NULL, FK -> rules.id | Rule that generated this |
| debit_account | VARCHAR(128) | NOT NULL | Account debited |
| credit_account | VARCHAR(128) | NOT NULL | Account credited |
| amount | DECIMAL(18,4) | NOT NULL | Entry amount |
| currency | CHAR(3) | NOT NULL | ISO 4217 |
| created_at | TIMESTAMP | NOT NULL DEFAULT NOW() | Creation time |

### nexus_blocks table

| Column | Type | Constraints | Description |
|---|---|---|---|
| nexus_id | VARCHAR(64) | PRIMARY KEY | Nexus transaction ID |
| action_id | VARCHAR(64) | NOT NULL, INDEX | LE action_id |
| parent_nexus_id | VARCHAR(64) | INDEX | Payment lifecycle grouper |
| status | VARCHAR(16) | NOT NULL | NOT_LIVE / LIVE / DEAD |
| version | INTEGER | NOT NULL | Incremented on update |
| data | JSONB | NOT NULL | Full Nexus transaction JSON |
| created_at | TIMESTAMP | NOT NULL DEFAULT NOW() | First seen |
| updated_at | TIMESTAMP | NOT NULL DEFAULT NOW() | Last update |

**Indexes:**
- `nexus_blocks`: `(parent_nexus_id)`, `(status)`, `(updated_at DESC)`
- `ledger_entries`: `(nexus_id)`, `(rule_id)`, `(created_at DESC)`

## Worked Example: Simple VISA Capture (Scenario 1)

This walkthrough traces a single capture event through all 9 pipeline steps.

**1. LE Simulator emits v1 (Gateway only)**
- `action_id`: `act_capture_001`, amount: `100.00 GBP`, scheme: VISA
- Only the Gateway (GW) pillar is present; COS, FIAPI, SD, Cash are absent

**2. Transformer produces NOT_LIVE Nexus event**
- Transaction written to `nexus_blocks` with `status = NOT_LIVE`
- No Trades or Legs emitted — insufficient data to assemble a complete transaction

**3. Rules Engine evaluates — no match**
- Rules Engine receives the `NOT_LIVE` event
- No rules fire because there are no legs to evaluate

**4. LE Simulator emits v2 (GW + COS + FIAPI)**
- Version 2 of the same `action_id` arrives with predicted fees and wallet impact
- COS and FIAPI pillars now present; SD and Cash still absent

**5. Transformer produces LIVE event**
- `status` updated to `LIVE`
- 2 Legs assembled: `SCHEME_SETTLEMENT (PREDICTED)` and `FUNDING (PREDICTED)`
- Updated Nexus transaction published to `nexus.blocks`

**6. Rules Engine fires**
- Rule matches on `LIVE` status with `SCHEME_SETTLEMENT` leg
- Generates ledger entry: debit `client_receivable`, credit `client_revenue`, `97.50 GBP`
- Entry written to `ledger_entries` and published to `nexus.ledger.entries`

**7. LE Simulator emits v3 (all pillars including SD)**
- Final version arrives with actual fees from the Scheme Data (SD) pillar

**8. Transformer updates to ACTUAL / SETTLED**
- Leg amounts updated from `PREDICTED` to `ACTUAL`
- Trade status updated to `SETTLED`
- New version of transaction published

**9. Rules Engine re-evaluates on SETTLED status**
- Rules configured for `SETTLED` status re-fire if conditions change
- Adjustment entries generated if actual fees differ from predicted
