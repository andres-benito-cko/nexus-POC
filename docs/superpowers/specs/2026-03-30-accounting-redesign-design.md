# Accounting Redesign — Design Spec

**Date:** 2026-03-30
**Status:** Approved
**Scope:** `rules-engine` service + `nexus-ui`

---

## Goals

1. Align the POC accounting engine with the transaction-lifecycle model described in the Nexus accounting paper.
2. Introduce a fully configurable Chart of Accounts as a first-class entity.
3. Enforce transaction-level balance controls: all postings for a transaction must net to zero per posting currency (hard reject).

---

## Background

The current `Rule` entity matches on `productType + transactionType + transactionStatus + legType` and fires a single `LedgerEntry` (debit / credit / amount). Accounts are plain strings — there is no formal Chart of Accounts. There is no balance validation.

The paper models entries across four lifecycle stages, triggered by `leg_status` transitions, with fee-level entries per `fee_type` and `passthrough` flag. This redesign closes the gap between the POC and the paper.

---

## Section 1 — Chart of Accounts

### Account entity

| Field | Type | Notes |
|-------|------|-------|
| `code` | `VARCHAR(100)` PK | Unique identifier referenced by rules (e.g. `scheme_clearing_settlement`) |
| `name` | `VARCHAR(255)` | Human-readable label |
| `accountType` | enum | `ASSET \| LIABILITY \| EQUITY \| REVENUE \| EXPENSE \| CONTROL` |
| `normalBalance` | enum | `DEBIT \| CREDIT` — the side that increases the account |
| `description` | `TEXT` | Optional; maps to the paper's account definitions |
| `enabled` | `BOOLEAN` | Default true; soft-delete pattern |
| `createdAt` | `TIMESTAMP` | Auto-set |

`CONTROL` covers clearing/reconciliation accounts (e.g. SDCA, accrued COS) that do not fit standard P&L or balance sheet categories.

### Constraints

- `code` is immutable after creation.
- Hard delete is blocked if any rule references the code; only soft delete (`enabled = false`) is permitted in that case.
- On rule save/update, the API validates that both `debitAccount` and `creditAccount` exist as enabled account codes.

### Seeded accounts (V6 migration)

All accounts named in the paper, with correct `accountType` and `normalBalance`:

| Code | Type | Normal Balance |
|------|------|---------------|
| `scheme_clearing_settlement` | CONTROL | DEBIT |
| `scheme_debtor` | ASSET | DEBIT |
| `scheme_debtor_holding` | ASSET | DEBIT |
| `client` | LIABILITY | CREDIT |
| `revenue` | REVENUE | CREDIT |
| `passthrough_cos` | EXPENSE | DEBIT |
| `accrued_cos` | LIABILITY | CREDIT |
| `non_passthrough_cos` | EXPENSE | DEBIT |
| `input_tax` | LIABILITY | CREDIT |
| `rolling_reserve` | LIABILITY | CREDIT |
| `expected_client_settlement` | LIABILITY | CREDIT |
| `cash` | ASSET | DEBIT |
| `scheme_fee_debtor` | ASSET | DEBIT |
| `scheme_fee_creditor` | LIABILITY | CREDIT |
| `intercompany_debtor_malpb` | ASSET | DEBIT |
| `intercompany_creditor_malpb` | LIABILITY | CREDIT |

---

## Section 2 — Rule entity

### New fields

| Field | Type | Notes |
|-------|------|-------|
| `firingContext` | enum | `LEG \| FEE`. Required. |
| `legStatus` | nullable string | Optional match criterion (e.g. `PREDICTED`, `ACTUAL`) |
| `feeType` | nullable string | e.g. `INTERCHANGE`, `SCHEME_FEE`, `PROCESSING_FEE`. Required when `firingContext = FEE`. |
| `passthrough` | nullable Boolean | FEE rules only. Null = match any. |

### Updated existing fields

- `transactionStatus` — remains optional (nullable); now independent of `legStatus`.
- `amountSource` — gains `fee_amount` as a valid value (joins `leg_amount`, `transaction_amount`). FEE rules must use `fee_amount`; enforced at API layer.
- `debitAccount` / `creditAccount` — validated as foreign keys against the `accounts` table on save/update.

### Validation rules (enforced at API layer)

| Condition | Rule |
|-----------|------|
| `firingContext = FEE` | `feeType` must be set; `amountSource` must be `fee_amount` |
| `firingContext = LEG` | `feeType` and `passthrough` must be null |
| Always | `debitAccount` and `creditAccount` must exist as enabled account codes |

### Match semantics

All non-null fields must match. A null field is a wildcard (matches any value). This is unchanged from the current implementation.

---

## Section 3 — Rule evaluation engine

The `RulesEngineService` restructures its evaluation loop to three nested levels: **transaction → leg → fee**.

### LedgerEntry model change

The current `LedgerEntry` stores both sides of a posting as a single row (`debitAccount` + `creditAccount` + `amount`). With that structure, every entry trivially contributes equal amounts to both sides — the balance validator would always pass and be meaningless.

This redesign restructures `LedgerEntry` as an **atomic posting**: one row = one side of a double-entry. The schema changes from `(debit_account, credit_account, amount)` to `(account, side, amount)` where `side` is `DEBIT | CREDIT`. When a rule matches, the engine generates **two** rows — one debit posting to the rule's `debitAccount` and one credit posting to the rule's `creditAccount` — sharing the same `amount`, `currency`, and parent context (`nexus_id`, `transaction_id`, `leg_id`, `rule_id`). The balance validator then sums across all rows for the transaction.

The V7 migration drops `debit_account` and `credit_account` from `ledger_entries` and adds `account VARCHAR(100)` and `side VARCHAR(6) NOT NULL`.

### Loop structure

For each `NexusBlock`:
- Rules are loaded once (all enabled rules) — no N+1.
- For each `transaction` in the block:
  1. For each `leg` in the transaction:
     - Evaluate all **LEG rules** against `(productType, transactionType, transactionStatus, legType, legStatus)`. Each match produces **two** `LedgerEntry` rows (one DEBIT, one CREDIT) using `leg_amount`.
     - For each `fee` on the leg:
       - Evaluate all **FEE rules** against the same leg criteria plus `(feeType, passthrough)`. Each match produces **two** `LedgerEntry` rows (one DEBIT, one CREDIT) using `fee_amount`.
  2. Run the **balance validator** on all entries collected for this transaction.
  3. If balanced: persist all entries atomically (single DB transaction) and publish to Kafka.
  4. If unbalanced: persist a `PostingError` record, publish to `nexus.posting.errors`, skip persistence of entries.

### Transaction isolation

Each Nexus transaction is processed in its own DB transaction boundary. A balance failure in one transaction does not affect other transactions in the same block. The Kafka offset for the block is committed only after all transactions are processed.

---

## Section 4 — Balance validator

### Algorithm

For each distinct `currency` in the computed `LedgerEntry` set for a transaction:

```
sum(entry.amount where entry is a debit)  ==  sum(entry.amount where entry is a credit)
```

Comparison is exact `BigDecimal` equality — no tolerance.

The transaction is balanced if and only if this holds for **every** currency in the set.

### Posting currency scope

The validator works in posting currency (the `currency` field on each entry, derived from `leg_currency` or `fee_currency`). Cross-currency netting is not performed — EUR entries and GBP entries must each balance independently.

### PostingError record

| Field | Notes |
|-------|-------|
| `id` | UUID |
| `nexusId` | From the Nexus block |
| `transactionId` | The transaction that failed |
| `currency` | The currency that failed to balance |
| `debitTotal` | Actual sum of debits in that currency |
| `creditTotal` | Actual sum of credits in that currency |
| `ruleIds` | Comma-separated list of rule UUIDs that fired — for diagnosis |
| `createdAt` | Timestamp |

The most common cause of imbalance is a missing rule (e.g. a fee type has no corresponding revenue entry). `ruleIds` makes this diagnosable without replaying the transaction.

---

## Section 5 — API and UI

### New endpoints (`rules-engine`)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/accounts` | List all accounts |
| `POST` | `/accounts` | Create account |
| `PUT` | `/accounts/{code}` | Update name, type, description, enabled |
| `DELETE` | `/accounts/{code}` | Soft delete (blocked if referenced by a rule) |
| `GET` | `/ledger/errors` | List posting errors; filterable by `nexusId`, `transactionId` |

### Modified endpoints

- `POST /rules` and `PUT /rules/{id}` — enforce the validation rules in Section 2.

### UI screens (`nexus-ui`)

**Chart of Accounts screen**
- Table: code, name, type, normal balance, enabled toggle.
- Inline create/edit form.
- Deleting an account referenced by a rule shows a warning listing dependent rules.

**Posting Errors screen**
- Table: nexus ID, transaction ID, currency, debit total, credit total, rule IDs that fired.
- Allows operators to identify missing or misconfigured rules.

**Rules screen (updated)**
- Adds columns: Firing Context, Leg Status, Fee Type, Passthrough.
- Fee Type and Passthrough are conditionally required/shown based on Firing Context.
- Account code dropdowns populated from the Chart of Accounts.

---

## Section 6 — Database migrations

### V5 — Create accounts table

```sql
CREATE TABLE accounts (
    code           VARCHAR(100) PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    account_type   VARCHAR(20)  NOT NULL,
    normal_balance VARCHAR(10)  NOT NULL,
    description    TEXT,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

### V6 — Seed accounts

Inserts all 16 accounts listed in Section 1 with correct types and normal balances.

### V7 — Alter rules table

```sql
ALTER TABLE rules
    ADD COLUMN firing_context VARCHAR(10)  NOT NULL DEFAULT 'LEG',
    ADD COLUMN leg_status     VARCHAR(50),
    ADD COLUMN fee_type       VARCHAR(50),
    ADD COLUMN passthrough    BOOLEAN;
```

Existing rows default to `firingContext = LEG` — backward compatible with current seed data.

### V8 — Restructure ledger_entries as atomic postings

```sql
ALTER TABLE ledger_entries
    DROP COLUMN debit_account,
    DROP COLUMN credit_account,
    ADD COLUMN account VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN side    VARCHAR(6)   NOT NULL DEFAULT 'DEBIT';
```

Existing rows (if any) will need to be cleared or re-processed — acceptable in a POC context. In production this would be a data migration.

### V9 — Create posting_errors table

```sql
CREATE TABLE posting_errors (
    id             UUID         PRIMARY KEY,
    nexus_id       VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    currency       VARCHAR(10)  NOT NULL,
    debit_total    NUMERIC(19,6) NOT NULL,
    credit_total   NUMERIC(19,6) NOT NULL,
    rule_ids       TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

---

## Out of scope

- Multi-currency netting / FX revaluation entries
- Account hierarchy (grouping accounts into trial balance sections)
- Per-entity chart of accounts (all accounts are global in this iteration)
- Replay / reprocessing of failed transactions
