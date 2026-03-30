# LE Simulator Improvements — Design

**Date:** 2026-03-30
**Status:** Approved
**Scope:** `le-simulator/` (Nexus-POC) + `research/le/` (Nexus)

---

## Context

The LE Simulator is a Spring Boot service that produces Linking Engine `linkedtransaction` events to Kafka for use by the Nexus Transformer in the POC pipeline. It currently has three problems that make it feel unrealistic:

1. **Pillar ordering is wrong.** `versioning.md` documents the production order as GW → COS → Balances/FIAPI → SD. The simulator does GW → Balances → COS → SD.
2. **Random mode doesn't simulate transaction evolution.** `randomCapture()` emits a single event at a random version with a brand-new `action_id` on every call. The transformer never sees a transaction evolve through versions — it only sees disconnected snapshots.
3. **Incomplete scheme coverage and hardcoded values.** All scenarios are hardcoded to `VISA`/`CKO_UK_LTD`. Schemes like Carte Bancaire (CB), Amex, JCB, and Discover are absent. CB exists in production but with incomplete pillar data; the simulator should produce CB as fully complete. Not all scenarios settle (Scenarios 07, 08, 09 lack SD events).

---

## Goals

- All transactions produced by the simulator reach a settled state (SD pillar present in the final version).
- Pillar arrival order matches production: GW → COS → Balances/FIAPI → SD.
- Full scheme coverage: VISA, Mastercard, Amex, CB, JCB, Discover — each with per-scheme realistic field values.
- CB is simulated as fully complete (all pillars including SD) even though production has data gaps.
- Random mode emits proper v1 → v2 → v3 → v4 sequences for each `action_id`.
- Happy paths only — no corrections, chargebacks, or replays.

---

## Non-Goals

- New scenario types beyond the existing 9.
- Negative paths (corrections, chargebacks, disputes).
- Changes to any service other than `le-simulator`.

---

## Phase 1 — BQ Research

Before implementation, validate existing hypotheses and fill gaps in `linking_engine_contract.md` using production data.

### Queries

| # | Purpose | Key fields |
|---|---|---|
| Q1 | Full scheme universe and relative volumes | `scheme_settlement_events[].metadata.scheme`, `cos_events[].metadata.payment_method`, `balances_changed_events[].metadata.payment_method` |
| Q2 | Transaction type mix for happy paths (settled only) | `scheme_settlement_events[].metadata.transaction_type` where SD is present |
| Q3 | Pillar arrival ordering | For each `action_id`, which version first introduced each pillar? Validate GW→COS→FIAPI→SD hypothesis |
| Q4 | Fee distributions per scheme | `cos_events[].payload.fee.value / gateway_events[].amount.value` — IC and scheme fee % by scheme |
| Q5 | Version count for settled transactions | `MAX(transaction_version)` where `scheme_settlement_events` is non-empty |
| Q6 | CB and minority scheme field shapes | Field presence/nullability for `scheme IN ('Carte Bancaire', ...)` |
| Q7 | Currency distribution per scheme | Which currencies appear with which schemes |
| Q8 | `metadata_type` / product type universe | `balances_changed_events[].metadata.metadata_type` — full value list and counts |
| Q9 | Settlement service name per scheme | `scheme_settlement_events[].metadata.settlement_service_name` grouped by scheme |
| Q10 | `payment_method` case behaviour | Is `VISA` vs `visa` pillar-dependent or inconsistent? |

### Output

Results are written to `research/le/production-distributions.md` in the Nexus repo, using the same format as `linking_engine_contract.md` (tables with sample period and query noted). `research/knowledge.md` is updated with a pointer to the new file. `linking_engine_contract.md` is updated at two points if Q3 or Q10 reveal corrections to current documentation.

---

## Phase 2 — Simulator Changes

All changes are in `le-simulator/`. No changes to other services.

### 2.1 Fix pillar arrival ordering

All 9 scenarios in `ScenarioLoader` are reordered from GW → Balances → COS → SD to **GW → COS → Balances → SD**, matching the production ordering documented in `research/le/versioning.md`.

### 2.2 All transactions settle

Every scenario ends with a version containing SD events.

| Scenario | Current final version | New final version |
|---|---|---|
| 01–06 | v4 with SD | unchanged (already settled) |
| 07 (Payout) | v3 with Cash | v4 adds SD |
| 08 (Top-up) | v3 with Cash | v4 adds SD |
| 09 (Cash Matched) | v2 with Balances | v3 adds SD |

The settled version carries actual (non-predicted) fee values in COS — reflecting the real predicted→actual progression that happens when SD arrives.

### 2.3 Full scheme coverage

`SchemeProfile` is a new standalone record class (`model/SchemeProfile.java`) that encapsulates per-scheme field values:

- `schemeName` — the value used in `scheme_settlement_events[].metadata.scheme`
- `paymentMethodBalances` — the casing used in `balances_changed_events[].metadata.payment_method`
- `paymentMethodCos` — the casing used in `cos_events[].metadata.payment_method`
- `defaultCurrency` — primary currency for this scheme
- `interchangeFeeRate` — IC fee as a fraction of transaction amount
- `schemeFeeRate` — scheme fee as a fraction of transaction amount
- `settlementServiceName` — typical value for `settlement_service_name`
- `acquirerCountry` — default ISO2 acquirer country

Profiles are populated from the BQ research output (Phase 1). Schemes covered: **VISA, Mastercard, Amex, Carte Bancaire, JCB, Discover**. CB is given a complete profile including SD fields even though production lacks them.

Each scenario builder method accepts a `SchemeProfile` parameter instead of hardcoded strings. The `loadScenario` method gains an optional `scheme` query parameter; if absent, it defaults to VISA. The random mode samples a scheme from the production volume distribution (Q1 output).

### 2.4 Fix random mode

`randomCapture()` and `randomRefund()` are replaced by a `RandomSequenceEmitter` that:

1. Generates a new `action_id` and `action_root_id`.
2. Emits v1 (GW only) to Kafka.
3. Waits for the configured `delayMs`.
4. Emits v2 (GW + COS) to Kafka.
5. Waits.
6. Emits v3 (GW + COS + Balances) to Kafka.
7. Waits.
8. Emits v4 (GW + COS + Balances + SD) to Kafka — the settled version.

Amounts, currencies, and schemes are sampled from production-derived distributions (Phase 1). Client entity IDs and legal entities are drawn from a small fixed set reflecting production entity variety rather than always using `cli_acme_corp`/`CKO_UK_LTD`.

`SimulatorService.startRandom()` is updated to use `RandomSequenceEmitter`. The existing `randomCapture()` and `randomRefund()` methods are removed.

---

## Data Dependencies

Phase 2 depends on Phase 1. The `SchemeProfile` values for fee rates, currency distributions, and settlement service names are left as clearly-marked constants in the code until Phase 1 results are available. Phase 1 results fill those constants before the code ships.

---

## Files Changed

**Nexus repo:**
- `research/le/production-distributions.md` — new, created from BQ query results
- `research/knowledge.md` — add pointer to new file
- `research/linking_engine_contract.md` — targeted corrections if Q3/Q10 require it

**Nexus-POC repo:**
- `le-simulator/src/main/java/com/checkout/nexus/lesimulator/service/ScenarioLoader.java` — reorder pillars, add `SchemeProfile`, scheme-parameterise all builders, update all 9 scenarios to settle
- `le-simulator/src/main/java/com/checkout/nexus/lesimulator/service/SimulatorService.java` — replace random generation with `RandomSequenceEmitter`
- `le-simulator/src/main/java/com/checkout/nexus/lesimulator/service/RandomSequenceEmitter.java` — new class
- `le-simulator/src/main/java/com/checkout/nexus/lesimulator/model/SchemeProfile.java` — new record
- `le-simulator/src/main/java/com/checkout/nexus/lesimulator/controller/SimulatorController.java` — add optional `scheme` field to `playScenario` request body
