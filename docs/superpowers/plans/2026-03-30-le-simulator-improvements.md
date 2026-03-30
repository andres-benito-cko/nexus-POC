# LE Simulator Improvements — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the LE Simulator produce realistic, fully-settled Linking Engine transactions across all supported card schemes including Carte Bancaire, with correct pillar arrival ordering and a properly sequential random mode.

**Architecture:** Two sequential phases. Phase 1 produces `research/le/production-distributions.md` in the Nexus repo containing 10 BQ SQL queries ready to run; a human fills in the results. Phase 2 implements four improvements in `le-simulator/`: fix pillar ordering (GW→COS→Balances→SD), universal settlement (SD on every final version), full scheme coverage via a new `SchemeProfile` record, and a corrected `RandomSequenceEmitter` that emits v1→v4 for the same `action_id`. Phase 2 code can be written before Phase 1 results arrive; `SchemeProfile` constants flagged `// TODO: BQ` are filled in Task 7 once results are available.

**Tech Stack:** Java 17, Spring Boot 3, Kafka (`spring-kafka`), Lombok, JUnit 5 + Mockito; BigQuery SQL (standard dialect)

**Repos involved:**
- `projects/Nexus/` — research documentation only (Tasks 1, 8, 9)
- `projects/Nexus-POC/le-simulator/` — all code changes (Tasks 2–7)

---

## File Map

**Created:**
- `projects/Nexus/research/le/production-distributions.md` — BQ query document with result tables
- `le-simulator/src/main/java/com/checkout/nexus/lesimulator/model/SchemeProfile.java` — per-scheme constants record
- `le-simulator/src/main/java/com/checkout/nexus/lesimulator/service/RandomSequenceEmitter.java` — v1→v4 sequential emitter
- `le-simulator/src/test/java/com/checkout/nexus/lesimulator/service/ScenarioLoaderTest.java`
- `le-simulator/src/test/java/com/checkout/nexus/lesimulator/service/RandomSequenceEmitterTest.java`
- `le-simulator/src/test/java/com/checkout/nexus/lesimulator/model/SchemeProfileTest.java`

**Modified:**
- `le-simulator/src/main/java/com/checkout/nexus/lesimulator/service/ScenarioLoader.java` — reorder pillars, scheme-parameterise, add SD to 07/08/09, add `buildRandomCaptureSequence`
- `le-simulator/src/main/java/com/checkout/nexus/lesimulator/service/SimulatorService.java` — inject `RandomSequenceEmitter`, update `playScenario` signature
- `le-simulator/src/main/java/com/checkout/nexus/lesimulator/controller/SimulatorController.java` — add `scheme` to request body
- `projects/Nexus/research/knowledge.md` — add pointer to new distributions doc
- `projects/Nexus/research/linking_engine_contract.md` — corrections from Q3/Q10 if needed

---

## Phase 1 — BQ Research

### Task 1: Create BQ query document (Nexus repo)

**Files:**
- Create: `projects/Nexus/research/le/production-distributions.md`

- [ ] **Step 1: Write the query document**

Create `projects/Nexus/research/le/production-distributions.md` with the following content. Each query block has an empty Results table below it for a human to fill in after running the query.

```markdown
# LE Production Distributions

> Derived from BigQuery production data.
> Table: `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction`
> Sample period: 2026-03-17 to 2026-03-20
> Status: QUERIES READY — results pending

---

## Q1a: Scheme Universe (SD pillar)

```sql
SELECT
  sse.metadata.scheme                AS scheme,
  COUNT(*)                           AS record_count,
  COUNT(DISTINCT action_id)          AS unique_action_ids
FROM `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction`,
  UNNEST(scheme_settlement_events) AS sse
WHERE DATE(linker_processing_timestamp) BETWEEN '2026-03-17' AND '2026-03-20'
GROUP BY 1
ORDER BY 2 DESC
LIMIT 50
```

**Results:**

| scheme | record_count | unique_action_ids |
|--------|-------------|-------------------|
| (fill in) | | |

---

## Q1b: Payment Method Universe (COS pillar)

```sql
SELECT
  ce.metadata.payment_method    AS payment_method,
  COUNT(*)                      AS count
FROM `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction`,
  UNNEST(cos_events) AS ce
WHERE DATE(linker_processing_timestamp) BETWEEN '2026-03-17' AND '2026-03-20'
  AND ce.metadata.payment_method IS NOT NULL
GROUP BY 1
ORDER BY 2 DESC
LIMIT 50
```

**Results:**

| payment_method | count |
|----------------|-------|
| (fill in) | |

---

## Q1c: Payment Method Universe (Balances pillar)

```sql
SELECT
  bce.metadata.payment_method   AS payment_method,
  COUNT(*)                      AS count
FROM `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction`,
  UNNEST(balances_changed_events) AS bce
WHERE DATE(linker_processing_timestamp) BETWEEN '2026-03-17' AND '2026-03-20'
  AND bce.metadata.payment_method IS NOT NULL
GROUP BY 1
ORDER BY 2 DESC
LIMIT 50
```

**Results:**

| payment_method | count |
|----------------|-------|
| (fill in) | |

---

## Q2: Transaction Type Mix (settled transactions only)

```sql
SELECT
  sse.metadata.scheme            AS scheme,
  sse.metadata.transaction_type  AS transaction_type,
  COUNT(*)                       AS count,
  ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (PARTITION BY sse.metadata.scheme), 2) AS pct_within_scheme
FROM `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction`,
  UNNEST(scheme_settlement_events) AS sse
WHERE DATE(linker_processing_timestamp) BETWEEN '2026-03-17' AND '2026-03-20'
GROUP BY 1, 2
ORDER BY 1, 3 DESC
LIMIT 200
```

**Results:**

| scheme | transaction_type | count | pct_within_scheme |
|--------|-----------------|-------|-------------------|
| (fill in) | | | |

---

## Q3: Pillar Arrival Ordering (settled transactions, all four pillars present)

```sql
WITH pillar_first_version AS (
  SELECT
    action_id,
    MIN(CASE WHEN ARRAY_LENGTH(gateway_events) > 0           THEN transaction_version END) AS first_gw,
    MIN(CASE WHEN ARRAY_LENGTH(cos_events) > 0               THEN transaction_version END) AS first_cos,
    MIN(CASE WHEN ARRAY_LENGTH(balances_changed_events) > 0  THEN transaction_version END) AS first_bal,
    MIN(CASE WHEN ARRAY_LENGTH(scheme_settlement_events) > 0 THEN transaction_version END) AS first_sd
  FROM `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction`
  WHERE DATE(linker_processing_timestamp) BETWEEN '2026-03-17' AND '2026-03-20'
  GROUP BY action_id
  HAVING
    MIN(CASE WHEN ARRAY_LENGTH(gateway_events) > 0           THEN transaction_version END) IS NOT NULL
    AND MIN(CASE WHEN ARRAY_LENGTH(cos_events) > 0           THEN transaction_version END) IS NOT NULL
    AND MIN(CASE WHEN ARRAY_LENGTH(balances_changed_events) > 0  THEN transaction_version END) IS NOT NULL
    AND MIN(CASE WHEN ARRAY_LENGTH(scheme_settlement_events) > 0 THEN transaction_version END) IS NOT NULL
)
SELECT
  CASE
    WHEN first_gw < first_cos AND first_cos < first_bal AND first_bal < first_sd THEN 'GW→COS→BAL→SD'
    WHEN first_gw < first_bal AND first_bal < first_cos AND first_cos < first_sd THEN 'GW→BAL→COS→SD'
    WHEN first_gw < first_cos AND first_cos = first_bal                          THEN 'GW→COS=BAL→SD'
    WHEN first_cos < first_gw                                                   THEN 'COS_BEFORE_GW'
    WHEN first_bal < first_gw                                                   THEN 'BAL_BEFORE_GW'
    ELSE 'OTHER'
  END AS pattern,
  COUNT(*)                                                                        AS count,
  ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2)                             AS pct
FROM pillar_first_version
GROUP BY 1
ORDER BY 2 DESC
```

**Results:**

| pattern | count | pct |
|---------|-------|-----|
| (fill in) | | |

---

## Q4: Fee Rates per Scheme (IC and scheme fee as % of transaction amount)

```sql
WITH latest_versions AS (
  SELECT action_id, MAX(transaction_version) AS max_version
  FROM `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction`
  WHERE DATE(linker_processing_timestamp) BETWEEN '2026-03-17' AND '2026-03-20'
    AND ARRAY_LENGTH(scheme_settlement_events) > 0
    AND ARRAY_LENGTH(cos_events) > 0
    AND ARRAY_LENGTH(gateway_events) > 0
  GROUP BY action_id
),
base AS (
  SELECT
    (SELECT sse.metadata.scheme FROM UNNEST(t.scheme_settlement_events) sse LIMIT 1)                                                       AS scheme,
    (SELECT gwe.amount.value   FROM UNNEST(t.gateway_events) gwe LIMIT 1)                                                                  AS gw_amount,
    (SELECT gwe.amount.currency_iso3_code FROM UNNEST(t.gateway_events) gwe LIMIT 1)                                                       AS currency,
    (SELECT SUM(ce.payload.fee.value) FROM UNNEST(t.cos_events) ce WHERE UPPER(ce.payload.fee_type) LIKE '%INTERCHANGE%')                  AS ic_fee,
    (SELECT SUM(ce.payload.fee.value) FROM UNNEST(t.cos_events) ce WHERE UPPER(ce.payload.fee_type) LIKE '%SCHEME%')                       AS scheme_fee
  FROM `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction` t
  INNER JOIN latest_versions lv ON t.action_id = lv.action_id AND t.transaction_version = lv.max_version
)
SELECT
  scheme,
  currency,
  ROUND(AVG(SAFE_DIVIDE(ic_fee, gw_amount)) * 100, 4)      AS avg_ic_fee_pct,
  ROUND(AVG(SAFE_DIVIDE(scheme_fee, gw_amount)) * 100, 4)  AS avg_scheme_fee_pct,
  COUNT(*)                                                   AS sample_size
FROM base
WHERE gw_amount > 0
GROUP BY 1, 2
ORDER BY 5 DESC
LIMIT 100
```

**Results:**

| scheme | currency | avg_ic_fee_pct | avg_scheme_fee_pct | sample_size |
|--------|----------|---------------|-------------------|-------------|
| (fill in) | | | | |

---

## Q5: Version Count Distribution for Settled Transactions

```sql
WITH settled AS (
  SELECT action_id, MAX(transaction_version) AS max_version
  FROM `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction`
  WHERE DATE(linker_processing_timestamp) BETWEEN '2026-03-17' AND '2026-03-20'
    AND ARRAY_LENGTH(scheme_settlement_events) > 0
  GROUP BY action_id
)
SELECT
  max_version,
  COUNT(*)                                              AS action_id_count,
  ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2)   AS pct
FROM settled
GROUP BY 1
ORDER BY 1
LIMIT 30
```

**Results:**

| max_version | action_id_count | pct |
|-------------|----------------|-----|
| (fill in) | | |

---

## Q6: Minority Scheme Field Shapes (CB and others)

First find all distinct scheme names:

```sql
SELECT DISTINCT sse.metadata.scheme
FROM `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction`,
  UNNEST(scheme_settlement_events) AS sse
WHERE DATE(linker_processing_timestamp) BETWEEN '2026-03-17' AND '2026-03-20'
ORDER BY 1
```

**Results — all distinct scheme names:**

| scheme |
|--------|
| (fill in) |

Then for each minority scheme (non-Visa/MC), check pillar completeness using the scheme name from above (replace `'<SCHEME_NAME>'`):

```sql
SELECT
  COUNT(*)                                                   AS total_records,
  COUNTIF(ARRAY_LENGTH(gateway_events) > 0)                 AS has_gw,
  COUNTIF(ARRAY_LENGTH(cos_events) > 0)                     AS has_cos,
  COUNTIF(ARRAY_LENGTH(balances_changed_events) > 0)        AS has_balances,
  COUNTIF(ARRAY_LENGTH(scheme_settlement_events) > 0)       AS has_sd,
  COUNTIF(ARRAY_LENGTH(cash_events) > 0)                    AS has_cash,
  (SELECT sse.metadata.settlement_service_name
   FROM UNNEST(scheme_settlement_events) sse
   WHERE sse.metadata.scheme = '<SCHEME_NAME>'
   LIMIT 1)                                                  AS sample_settlement_service,
  (SELECT bce.metadata.payment_method
   FROM UNNEST(balances_changed_events) bce
   LIMIT 1)                                                  AS sample_bal_payment_method,
  (SELECT ce.metadata.payment_method
   FROM UNNEST(cos_events) ce
   LIMIT 1)                                                  AS sample_cos_payment_method
FROM `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction`
WHERE DATE(linker_processing_timestamp) BETWEEN '2026-03-17' AND '2026-03-20'
  AND EXISTS (SELECT 1 FROM UNNEST(scheme_settlement_events) sse WHERE sse.metadata.scheme = '<SCHEME_NAME>')
```

**Results (run for each minority scheme — CB, Amex, JCB, Discover, etc.):**

| scheme | total_records | has_gw | has_cos | has_balances | has_sd | has_cash | settlement_service | bal_pm | cos_pm |
|--------|--------------|--------|---------|-------------|--------|----------|--------------------|--------|--------|
| (fill in) | | | | | | | | | |

---

## Q7: Currency Distribution per Scheme

```sql
SELECT
  sse.metadata.scheme              AS scheme,
  gwe.amount.currency_iso3_code    AS currency,
  COUNT(*)                         AS count,
  ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (PARTITION BY sse.metadata.scheme), 2) AS pct_within_scheme
FROM `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction`,
  UNNEST(scheme_settlement_events) AS sse,
  UNNEST(gateway_events)           AS gwe
WHERE DATE(linker_processing_timestamp) BETWEEN '2026-03-17' AND '2026-03-20'
GROUP BY 1, 2
ORDER BY 1, 3 DESC
LIMIT 200
```

**Results:**

| scheme | currency | count | pct_within_scheme |
|--------|----------|-------|-------------------|
| (fill in) | | | |

---

## Q8: Metadata Type (product type) Universe

```sql
SELECT
  bce.metadata.metadata_type   AS metadata_type,
  COUNT(*)                     AS count
FROM `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction`,
  UNNEST(balances_changed_events) AS bce
WHERE DATE(linker_processing_timestamp) BETWEEN '2026-03-17' AND '2026-03-20'
  AND bce.metadata.metadata_type IS NOT NULL
GROUP BY 1
ORDER BY 2 DESC
LIMIT 50
```

**Results:**

| metadata_type | count |
|---------------|-------|
| (fill in) | |

---

## Q9: Settlement Service Name per Scheme

```sql
SELECT
  sse.metadata.scheme                  AS scheme,
  sse.metadata.settlement_service_name AS settlement_service_name,
  COUNT(*)                             AS count,
  ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (PARTITION BY sse.metadata.scheme), 2) AS pct_within_scheme
FROM `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction`,
  UNNEST(scheme_settlement_events) AS sse
WHERE DATE(linker_processing_timestamp) BETWEEN '2026-03-17' AND '2026-03-20'
GROUP BY 1, 2
ORDER BY 1, 3 DESC
LIMIT 100
```

**Results:**

| scheme | settlement_service_name | count | pct_within_scheme |
|--------|------------------------|-------|-------------------|
| (fill in) | | | |

---

## Q10: payment_method Casing Cross-Pillar

```sql
SELECT
  (SELECT sse.metadata.scheme
   FROM UNNEST(scheme_settlement_events) sse LIMIT 1)            AS scheme,
  (SELECT bce.metadata.payment_method
   FROM UNNEST(balances_changed_events) bce LIMIT 1)             AS pm_balances,
  (SELECT ce.metadata.payment_method
   FROM UNNEST(cos_events) ce LIMIT 1)                           AS pm_cos,
  COUNT(*)                                                        AS count
FROM `cko-data-tl-prod-2489.source_flink.financial_infrastructure_transactional_ledger_linking_engine_linkedtransaction`
WHERE DATE(linker_processing_timestamp) BETWEEN '2026-03-17' AND '2026-03-20'
  AND ARRAY_LENGTH(scheme_settlement_events) > 0
  AND ARRAY_LENGTH(balances_changed_events) > 0
  AND ARRAY_LENGTH(cos_events) > 0
GROUP BY 1, 2, 3
ORDER BY 4 DESC
LIMIT 100
```

**Results:**

| scheme | pm_balances | pm_cos | count |
|--------|-------------|--------|-------|
| (fill in) | | | |
```

- [ ] **Step 2: Commit**

```bash
cd projects/Nexus
git add research/le/production-distributions.md
git commit -m "docs: add BQ production distribution queries (results pending)"
```

> ⛔ **GATE — human step required before Task 9:**
> Run all queries in BigQuery, fill in the Results tables in `production-distributions.md`, then continue with Task 8 (update knowledge.md) and Task 9 (fill SchemeProfile constants).
> Tasks 2–7 can proceed in parallel.

---

## Phase 2 — SchemeProfile

### Task 2: Create SchemeProfile with placeholder constants + tests

**Files:**
- Create: `le-simulator/src/main/java/com/checkout/nexus/lesimulator/model/SchemeProfile.java`
- Create: `le-simulator/src/test/java/com/checkout/nexus/lesimulator/model/SchemeProfileTest.java`

- [ ] **Step 1: Write the failing test**

```java
// le-simulator/src/test/java/com/checkout/nexus/lesimulator/model/SchemeProfileTest.java
package com.checkout.nexus.lesimulator.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchemeProfileTest {

    @Test
    void allProfilesHaveRequiredFields() {
        for (SchemeProfile p : SchemeProfile.ALL) {
            assertNotNull(p.schemeName(),             p.schemeName() + ": schemeName null");
            assertNotNull(p.paymentMethodBalances(),  p.schemeName() + ": paymentMethodBalances null");
            assertNotNull(p.paymentMethodCos(),       p.schemeName() + ": paymentMethodCos null");
            assertNotNull(p.defaultCurrency(),        p.schemeName() + ": defaultCurrency null");
            assertNotNull(p.settlementServiceName(),  p.schemeName() + ": settlementServiceName null");
            assertNotNull(p.acquirerCountry(),        p.schemeName() + ": acquirerCountry null");
            assertTrue(p.interchangeFeeRate() >= 0,   p.schemeName() + ": negative IC rate");
            assertTrue(p.schemeFeeRate() >= 0,        p.schemeName() + ": negative scheme rate");
        }
    }

    @Test
    void volumeWeightsSumToOne() {
        double sum = 0;
        for (double w : SchemeProfile.VOLUME_WEIGHTS) {
            sum += w;
        }
        assertEquals(1.0, sum, 0.001);
    }

    @Test
    void volumeWeightsCountMatchesProfileCount() {
        assertEquals(SchemeProfile.ALL.size(), SchemeProfile.VOLUME_WEIGHTS.length);
    }

    @Test
    void sixSchemesAreDefined() {
        assertEquals(6, SchemeProfile.ALL.size());
    }
}
```

- [ ] **Step 2: Run the test — expect compilation failure**

```bash
cd le-simulator && ./gradlew test --tests "com.checkout.nexus.lesimulator.model.SchemeProfileTest" 2>&1 | tail -10
```

Expected: compilation error — `SchemeProfile` does not exist.

- [ ] **Step 3: Create SchemeProfile.java**

```java
// le-simulator/src/main/java/com/checkout/nexus/lesimulator/model/SchemeProfile.java
package com.checkout.nexus.lesimulator.model;

import java.util.List;

/**
 * Per-scheme field values used by ScenarioLoader and RandomSequenceEmitter.
 *
 * Fields marked "TODO: BQ Qn" must be updated from production-distributions.md
 * results before this plan is considered complete (see Task 9).
 */
public record SchemeProfile(
    String schemeName,
    String paymentMethodBalances,
    String paymentMethodCos,
    String defaultCurrency,
    double interchangeFeeRate,
    double schemeFeeRate,
    String settlementServiceName,
    String acquirerCountry
) {

    public static final SchemeProfile VISA = new SchemeProfile(
        "Visa",
        "VISA",        // TODO: BQ Q10 confirm casing
        "visa",        // TODO: BQ Q10 confirm casing
        "EUR",
        0.0048,        // TODO: BQ Q4 avg_ic_fee_pct / 100
        0.0009,        // TODO: BQ Q4 avg_scheme_fee_pct / 100
        "ISS",         // TODO: BQ Q9 dominant service for Visa
        "GB"
    );

    public static final SchemeProfile MASTERCARD = new SchemeProfile(
        "Mastercard",
        "MASTERCARD",  // TODO: BQ Q10
        "mastercard",  // TODO: BQ Q10
        "EUR",
        0.0050,        // TODO: BQ Q4
        0.0010,        // TODO: BQ Q4
        "EANSS",       // TODO: BQ Q9
        "GB"
    );

    public static final SchemeProfile AMEX = new SchemeProfile(
        "Amex",
        "AMEX",        // TODO: BQ Q10 (may not appear in Balances if no COS)
        "amex",        // TODO: BQ Q10
        "USD",         // TODO: BQ Q7 confirm dominant currency
        0.0175,        // TODO: BQ Q4
        0.0000,        // TODO: BQ Q4 (Amex typically bundles fees)
        "EANSS",       // TODO: BQ Q9
        "GB"
    );

    // Carte Bancaire is simulated as complete even though production data has gaps.
    // All field values require BQ validation — extrapolated from Visa/MC structure.
    public static final SchemeProfile CARTE_BANCAIRE = new SchemeProfile(
        "Carte Bancaire", // TODO: BQ Q6 — confirm exact scheme name string in production
        "CB",             // TODO: BQ Q10 Q6
        "cb",             // TODO: BQ Q10 Q6
        "EUR",
        0.0020,           // TODO: BQ Q4 (if data exists) else use regulatory cap ~0.2%
        0.0005,           // TODO: BQ Q4
        "EU00082602",     // TODO: BQ Q9
        "FR"
    );

    public static final SchemeProfile JCB = new SchemeProfile(
        "JCB",
        "JCB",         // TODO: BQ Q10 Q6
        "jcb",         // TODO: BQ Q10 Q6 — contract doc shows "jcb" in COS
        "JPY",         // TODO: BQ Q7 confirm dominant currency
        0.0140,        // TODO: BQ Q4
        0.0000,        // TODO: BQ Q4
        "AP00070201",  // TODO: BQ Q9
        "JP"
    );

    public static final SchemeProfile DISCOVER = new SchemeProfile(
        "Discover",
        "DISCOVER",    // TODO: BQ Q10 Q6
        "discover",    // TODO: BQ Q10 Q6 — contract doc shows "discover" in COS
        "USD",         // TODO: BQ Q7
        0.0155,        // TODO: BQ Q4
        0.0000,        // TODO: BQ Q4
        "US00000001",  // TODO: BQ Q9
        "US"
    );

    /** All profiles in volume order (highest → lowest). */
    public static final List<SchemeProfile> ALL = List.of(
        VISA, MASTERCARD, AMEX, CARTE_BANCAIRE, JCB, DISCOVER
    );

    /**
     * Volume weights for random sampling, indexed parallel to ALL.
     * TODO: BQ Q1a — replace with actual production volume distribution.
     * Current values are rough estimates only.
     */
    public static final double[] VOLUME_WEIGHTS = {0.43, 0.40, 0.10, 0.05, 0.01, 0.01};
}
```

- [ ] **Step 4: Run tests — expect all to pass**

```bash
cd le-simulator && ./gradlew test --tests "com.checkout.nexus.lesimulator.model.SchemeProfileTest"
```

Expected output: `4 tests completed, 0 failed`

- [ ] **Step 5: Commit**

```bash
cd le-simulator
git add src/main/java/com/checkout/nexus/lesimulator/model/SchemeProfile.java \
        src/test/java/com/checkout/nexus/lesimulator/model/SchemeProfileTest.java
git commit -m "feat: add SchemeProfile with placeholder constants for 6 schemes"
```

---

## Phase 3 — ScenarioLoader

### Task 3: Fix pillar ordering + scheme-parameterise ScenarioLoader (scenarios 01–06)

**Files:**
- Modify: `le-simulator/src/main/java/com/checkout/nexus/lesimulator/service/ScenarioLoader.java`
- Create: `le-simulator/src/test/java/com/checkout/nexus/lesimulator/service/ScenarioLoaderTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// le-simulator/src/test/java/com/checkout/nexus/lesimulator/service/ScenarioLoaderTest.java
package com.checkout.nexus.lesimulator.service;

import com.checkout.nexus.lesimulator.model.LeLinkedTransaction;
import com.checkout.nexus.lesimulator.model.SchemeProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioLoaderTest {

    private final ScenarioLoader loader = new ScenarioLoader();

    // --- Pillar ordering ---

    @ParameterizedTest
    @ValueSource(strings = {"01", "02", "03", "04", "05", "06"})
    void acquiringScenarios_v1HasOnlyGateway(String scenarioId) {
        List<LeLinkedTransaction> versions = loader.loadScenario(scenarioId, SchemeProfile.VISA);
        LeLinkedTransaction v1 = versions.get(0);
        assertEquals(1, v1.getTransactionVersion());
        assertFalse(v1.getGatewayEvents().isEmpty(), "v1 must have GW");
        assertTrue(v1.getCosEvents().isEmpty(),               "v1 must not have COS");
        assertTrue(v1.getBalancesChangedEvents().isEmpty(),   "v1 must not have Balances");
        assertTrue(v1.getSchemeSettlementEvents().isEmpty(),  "v1 must not have SD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "02", "03", "04", "05", "06"})
    void acquiringScenarios_v2AddsCosBeforeBalances(String scenarioId) {
        List<LeLinkedTransaction> versions = loader.loadScenario(scenarioId, SchemeProfile.VISA);
        LeLinkedTransaction v2 = versions.get(1);
        assertEquals(2, v2.getTransactionVersion());
        assertFalse(v2.getCosEvents().isEmpty(),             "v2 must have COS");
        assertTrue(v2.getBalancesChangedEvents().isEmpty(),  "v2 must not yet have Balances");
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "02", "03", "04", "05", "06"})
    void acquiringScenarios_v3AddsBalances(String scenarioId) {
        List<LeLinkedTransaction> versions = loader.loadScenario(scenarioId, SchemeProfile.VISA);
        LeLinkedTransaction v3 = versions.get(2);
        assertEquals(3, v3.getTransactionVersion());
        assertFalse(v3.getBalancesChangedEvents().isEmpty(),    "v3 must have Balances");
        assertTrue(v3.getSchemeSettlementEvents().isEmpty(),    "v3 must not yet have SD");
    }

    // --- Settlement ---

    @ParameterizedTest
    @ValueSource(strings = {"01", "02", "03", "04", "05", "06", "07", "08", "09"})
    void allScenarios_lastVersionHasSD(String scenarioId) {
        List<LeLinkedTransaction> versions = loader.loadScenario(scenarioId, SchemeProfile.VISA);
        LeLinkedTransaction last = versions.get(versions.size() - 1);
        assertFalse(last.getSchemeSettlementEvents().isEmpty(),
            "Scenario " + scenarioId + " last version must have SD");
    }

    // --- Scheme reflection ---

    @Test
    void scheme_sdMetadataReflectsSchemeProfile() {
        List<LeLinkedTransaction> versions = loader.loadScenario("01", SchemeProfile.CARTE_BANCAIRE);
        LeLinkedTransaction last = versions.get(versions.size() - 1);
        assertEquals("Carte Bancaire",
            last.getSchemeSettlementEvents().get(0).getMetadata().getScheme());
    }

    @Test
    void scheme_cosPaymentMethodReflectsSchemeProfile() {
        List<LeLinkedTransaction> versions = loader.loadScenario("01", SchemeProfile.MASTERCARD);
        // v2 is COS
        assertFalse(versions.get(1).getCosEvents().isEmpty());
        assertEquals("mastercard",
            versions.get(1).getCosEvents().get(0).getMetadata().getPaymentMethod());
    }

    @Test
    void scheme_balancesPaymentMethodReflectsSchemeProfile() {
        List<LeLinkedTransaction> versions = loader.loadScenario("01", SchemeProfile.MASTERCARD);
        // v3 is Balances
        assertFalse(versions.get(2).getBalancesChangedEvents().isEmpty());
        assertEquals("MASTERCARD",
            versions.get(2).getBalancesChangedEvents().get(0).getMetadata().getPaymentMethod());
    }

    // --- Shared action_id ---

    @ParameterizedTest
    @ValueSource(strings = {"01", "02", "03", "04", "05", "06", "07", "08", "09"})
    void allScenarios_versionsShareActionId(String scenarioId) {
        List<LeLinkedTransaction> versions = loader.loadScenario(scenarioId, SchemeProfile.VISA);
        String actionId = versions.get(0).getActionId();
        versions.forEach(v -> assertEquals(actionId, v.getActionId(),
            "Scenario " + scenarioId + " version " + v.getTransactionVersion() + " has wrong actionId"));
    }

    // --- Random sequence ---

    @Test
    void buildRandomCaptureSequence_produces4VersionsInOrder() {
        List<LeLinkedTransaction> versions = loader.buildRandomCaptureSequence(SchemeProfile.VISA, 100.0, "cli_test");
        assertEquals(4, versions.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(i + 1, versions.get(i).getTransactionVersion());
        }
    }

    @Test
    void buildRandomCaptureSequence_allVersionsShareActionId() {
        List<LeLinkedTransaction> versions = loader.buildRandomCaptureSequence(SchemeProfile.VISA, 100.0, "cli_test");
        String actionId = versions.get(0).getActionId();
        versions.forEach(v -> assertEquals(actionId, v.getActionId()));
    }

    @Test
    void buildRandomCaptureSequence_v1GwOnly_v4Settled() {
        List<LeLinkedTransaction> versions = loader.buildRandomCaptureSequence(SchemeProfile.VISA, 100.0, "cli_test");
        LeLinkedTransaction v1 = versions.get(0);
        assertTrue(v1.getCosEvents().isEmpty());
        assertTrue(v1.getBalancesChangedEvents().isEmpty());
        assertFalse(versions.get(3).getSchemeSettlementEvents().isEmpty());
    }
}
```

- [ ] **Step 2: Run tests — expect failure**

```bash
cd le-simulator && ./gradlew test --tests "com.checkout.nexus.lesimulator.service.ScenarioLoaderTest" 2>&1 | tail -20
```

Expected: multiple failures — `loadScenario(String, SchemeProfile)` doesn't exist, pillar ordering is wrong.

- [ ] **Step 3: Replace ScenarioLoader.java with the updated implementation**

Replace the entire file content:

```java
package com.checkout.nexus.lesimulator.service;

import com.checkout.nexus.lesimulator.model.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class ScenarioLoader {

    public record ScenarioInfo(String id, String name, String description, int versionCount) {}

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_DATE_TIME;
    private static final String TODAY     = LocalDate.now().toString();
    private static final String TOMORROW  = LocalDate.now().plusDays(1).toString();
    private static final String DAY_AFTER = LocalDate.now().plusDays(2).toString();
    private static final String NOW       = LocalDateTime.now().format(DT_FMT) + "Z";

    // Hardcoded entity set; expand from BQ data if desired.
    private static final String[][] ENTITY_PAIRS = {
        {"cli_acme_corp",  "CKO_UK_LTD"},
        {"cli_global_ltd", "CKO_DE_GMBH"},
        {"cli_retail_eu",  "CKO_UK_LTD"},
        {"cli_fintech_uk", "CKO_UK_LTD"},
    };

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public List<ScenarioInfo> listScenarios() {
        return List.of(
            new ScenarioInfo("01", "Acquiring Capture (Simple)",                   "Simple card capture, same-entity",               4),
            new ScenarioInfo("02", "Acquiring Capture (Cross-Currency)",            "Cross-currency capture GBP→EUR",                 4),
            new ScenarioInfo("03", "Acquiring Capture (Cross-Entity)",              "Cross-entity capture with TRANSFER trade",       4),
            new ScenarioInfo("04", "Acquiring Capture (Cross-Currency+Cross-Entity)", "Combined cross-currency and cross-entity",     4),
            new ScenarioInfo("05", "Acquiring Refund",                              "Partial refund on a previous capture",           4),
            new ScenarioInfo("06", "Acquiring Chargeback",                          "Chargeback received from scheme",                4),
            new ScenarioInfo("07", "Payout",                                        "Card payout to client",                         4),
            new ScenarioInfo("08", "Top-up",                                        "Client top-up via bank transfer",               4),
            new ScenarioInfo("09", "Cash Matched Settlement",                       "Cash settlement matched to batch",               3)
        );
    }

    /** Loads a scenario using the default VISA scheme profile. */
    public List<LeLinkedTransaction> loadScenario(String scenarioId) {
        return loadScenario(scenarioId, SchemeProfile.VISA);
    }

    /** Loads a scenario using the supplied scheme profile. */
    public List<LeLinkedTransaction> loadScenario(String scenarioId, SchemeProfile scheme) {
        return switch (scenarioId) {
            case "01" -> buildAcquiringCaptureSimple(scheme);
            case "02" -> buildAcquiringCaptureCrossCurrency(scheme);
            case "03" -> buildAcquiringCaptureCrossEntity(scheme);
            case "04" -> buildAcquiringCaptureCrossCurrencyCrossEntity(scheme);
            case "05" -> buildAcquiringRefund(scheme);
            case "06" -> buildAcquiringChargeback(scheme);
            case "07" -> buildPayout(scheme);
            case "08" -> buildTopup(scheme);
            case "09" -> buildCashMatchedSettlement(scheme);
            default   -> throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
        };
    }

    /**
     * Builds a 4-version random capture sequence for the given scheme, amount, and client.
     * Pillar order: GW → COS → Balances → SD.
     */
    public List<LeLinkedTransaction> buildRandomCaptureSequence(SchemeProfile scheme, double amount, String clientId) {
        String actionId    = "act_rnd_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_rnd_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env  = envelope(actionId, actionRootId);
        String ccy         = scheme.defaultCurrency();

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayCapture(env, amount, ccy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setCosEvents(List.of(
            cosEvent(env, r2(amount * scheme.interchangeFeeRate()), ccy, "INTERCHANGE_FEE", true,  scheme),
            cosEvent(env, r2(amount * scheme.schemeFeeRate()),      ccy, "SCHEME_FEE",      true,  scheme)
        ));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setBalancesChangedEvents(List.of(balancesCapture(env, clientId, "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TOMORROW, r2(amount * 0.02), scheme)));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            scheme, "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", clientId,
            List.of(sdFee("INTERCHANGE_FEE", r2(amount * scheme.interchangeFeeRate()), ccy),
                    sdFee("SCHEME_FEE",      r2(amount * scheme.schemeFeeRate()),      ccy)))));

        return List.of(v1, v2, v3, v4);
    }

    // -------------------------------------------------------------------------
    // Scenario builders — GW → COS → Balances → SD
    // -------------------------------------------------------------------------

    private List<LeLinkedTransaction> buildAcquiringCaptureSimple(SchemeProfile s) {
        String actionId     = "act_cap_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_"     + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        String ccy          = s.defaultCurrency();
        double amount       = 100.00;
        double reserve      = r2(amount * 0.02);

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayCapture(env, amount, ccy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setCosEvents(List.of(
            cosEvent(env, r2(amount * s.interchangeFeeRate()), ccy, "INTERCHANGE_FEE", true,  s),
            cosEvent(env, r2(amount * s.schemeFeeRate()),      ccy, "SCHEME_FEE",      true,  s)
        ));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setBalancesChangedEvents(List.of(balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TOMORROW, reserve, s)));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            s, "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp",
            List.of(sdFee("INTERCHANGE_FEE", r2(amount * s.interchangeFeeRate()), ccy),
                    sdFee("SCHEME_FEE",      r2(amount * s.schemeFeeRate()),      ccy)))));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildAcquiringCaptureCrossCurrency(SchemeProfile s) {
        String actionId     = "act_cap_cc_"  + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_cc_"      + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        // Cross-currency: process in GBP, hold in EUR
        double processingAmount = 85.00;
        double holdingAmount    = 100.00;
        String processingCcy    = "GBP";
        String holdingCcy       = "EUR";

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayCapture(env, processingAmount, processingCcy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setCosEvents(List.of(
            cosEvent(env, r2(processingAmount * s.interchangeFeeRate()), processingCcy, "INTERCHANGE_FEE", true, s),
            cosEvent(env, r2(processingAmount * s.schemeFeeRate()),      processingCcy, "SCHEME_FEE",      true, s)
        ));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setBalancesChangedEvents(List.of(balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            holdingAmount, holdingCcy, processingAmount, processingCcy, TOMORROW, r2(holdingAmount * 0.02), s)));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, processingAmount, processingCcy, processingAmount, processingCcy,
            s, "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp",
            List.of(sdFee("INTERCHANGE_FEE", r2(processingAmount * s.interchangeFeeRate()), processingCcy),
                    sdFee("SCHEME_FEE",      r2(processingAmount * s.schemeFeeRate()),      processingCcy)))));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildAcquiringCaptureCrossEntity(SchemeProfile s) {
        String actionId     = "act_cap_ce_"  + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_ce_"      + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        String ccy          = s.defaultCurrency();
        double amount       = 200.00;

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayCapture(env, amount, ccy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setCosEvents(List.of(
            cosEvent(env, r2(amount * s.interchangeFeeRate()), ccy, "INTERCHANGE_FEE", true, s),
            cosEvent(env, r2(amount * s.schemeFeeRate()),      ccy, "SCHEME_FEE",      true, s)
        ));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        // Cross-entity: settlement entity (CKO_DE_GMBH) differs from acquirer (CKO_UK_LTD)
        v3.setBalancesChangedEvents(List.of(balancesCapture(env, "cli_global_ltd", "CKO_DE_GMBH", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TOMORROW, r2(amount * 0.02), s)));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            s, "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_DE_GMBH", "cli_global_ltd",
            List.of(sdFee("INTERCHANGE_FEE", r2(amount * s.interchangeFeeRate()), ccy),
                    sdFee("SCHEME_FEE",      r2(amount * s.schemeFeeRate()),      ccy)))));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildAcquiringCaptureCrossCurrencyCrossEntity(SchemeProfile s) {
        String actionId     = "act_cap_ccce_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_ccce_"     + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        double amount       = 150.00;
        String processingCcy = "USD";
        String holdingCcy    = "EUR";
        double holdingAmount = 135.00;

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayCapture(env, amount, processingCcy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setCosEvents(List.of(
            cosEvent(env, r2(amount * s.interchangeFeeRate()), processingCcy, "INTERCHANGE_FEE", true, s),
            cosEvent(env, r2(amount * s.schemeFeeRate()),      processingCcy, "SCHEME_FEE",      true, s)
        ));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setBalancesChangedEvents(List.of(balancesCapture(env, "cli_global_ltd", "CKO_DE_GMBH", "CKO_UK_LTD",
            holdingAmount, holdingCcy, amount, processingCcy, TOMORROW, r2(holdingAmount * 0.02), s)));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, processingCcy, amount, processingCcy,
            s, "Capture", DAY_AFTER, "CKO_UK_LTD", "CKO_DE_GMBH", "cli_global_ltd",
            List.of(sdFee("INTERCHANGE_FEE", r2(amount * s.interchangeFeeRate()), processingCcy),
                    sdFee("SCHEME_FEE",      r2(amount * s.schemeFeeRate()),      processingCcy)))));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildAcquiringRefund(SchemeProfile s) {
        String actionId     = "act_ref_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_"     + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        String ccy          = s.defaultCurrency();
        double amount       = 50.00;

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayEvent(env, "payment_refunded", amount, ccy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setCosEvents(List.of(
            cosEvent(env, r2(amount * s.schemeFeeRate()), ccy, "SCHEME_FEE", true, s)
        ));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TOMORROW, 0, s);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_REFUND");
        bce.getMetadata().setSourceEventType("payment_refunded");
        bce.getMetadata().setEnhancedActionType("PartialRefund");
        v3.setBalancesChangedEvents(List.of(bce));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            s, "Refund", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp", List.of())));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildAcquiringChargeback(SchemeProfile s) {
        String actionId     = "act_cb_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_"    + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        String ccy          = s.defaultCurrency();
        double amount       = 100.00;

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayEvent(env, "payment_chargeback", amount, ccy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        v2.setCosEvents(List.of(
            cosEvent(env, r2(amount * s.schemeFeeRate()), ccy, "SCHEME_FEE", true, s)
        ));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TOMORROW, 0, s);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_CHARGEBACK");
        bce.getMetadata().setSourceEventType("payment_chargeback");
        v3.setBalancesChangedEvents(List.of(bce));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            s, "Chargeback", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp",
            List.of(sdFee("SCHEME_FEE", 25.00, ccy)))));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildPayout(SchemeProfile s) {
        String actionId     = "act_po_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_po_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        double amount       = 500.00;
        String ccy          = "GBP"; // Payouts skew GBP regardless of scheme

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayEvent(env, "payment_paid_out", amount, ccy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TOMORROW, 0, s);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_CARD_PAYOUT");
        bce.getMetadata().setSourceEventType("payment_paid_out");
        v2.setBalancesChangedEvents(List.of(bce));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setCashEvents(List.of(cashEvent(env, amount, ccy, "credit", "CKO_UK_LTD", "cli_acme_corp", TOMORROW)));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            s, "PayToCard", DAY_AFTER, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp", List.of())));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildTopup(SchemeProfile s) {
        String actionId     = "act_tu_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_tu_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        double amount       = 1000.00;
        String ccy          = "EUR";

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .gatewayEvents(List.of(gatewayEvent(env, "payment_top_up", amount, ccy, actionRootId)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TODAY, 0, s);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_TOP_UP");
        bce.getMetadata().setSourceEventType("payment_top_up");
        v2.setBalancesChangedEvents(List.of(bce));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setCashEvents(List.of(cashEvent(env, amount, ccy, "debit", "CKO_UK_LTD", "cli_acme_corp", TODAY)));

        LeLinkedTransaction v4 = copyWithVersion(v3, 4);
        v4.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            s, "Capture", TODAY, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp", List.of())));

        return List.of(v1, v2, v3, v4);
    }

    private List<LeLinkedTransaction> buildCashMatchedSettlement(SchemeProfile s) {
        String actionId     = "act_cash_" + UUID.randomUUID().toString().substring(0, 8);
        String actionRootId = "pay_cash_" + UUID.randomUUID().toString().substring(0, 8);
        EventEnvelope env   = envelope(actionId, actionRootId);
        double amount       = 5000.00;
        String ccy          = "EUR";

        LeLinkedTransaction v1 = LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(actionId).actionRootId(actionRootId).transactionVersion(1)
            .cashEvents(List.of(cashEvent(env, amount, ccy, "credit", "CKO_UK_LTD", "cli_acme_corp", TODAY)))
            .build();

        LeLinkedTransaction v2 = copyWithVersion(v1, 2);
        BalancesChangedEvent bce = balancesCapture(env, "cli_acme_corp", "CKO_UK_LTD", "CKO_UK_LTD",
            amount, ccy, amount, ccy, TODAY, 0, s);
        bce.getMetadata().setActionType("BALANCES_CHANGED_ACTION_TYPE_CREDIT");
        v2.setBalancesChangedEvents(List.of(bce));

        LeLinkedTransaction v3 = copyWithVersion(v2, 3);
        v3.setSchemeSettlementEvents(List.of(sdEvent(env, amount, ccy, amount, ccy,
            s, "Capture", TODAY, "CKO_UK_LTD", "CKO_UK_LTD", "cli_acme_corp", List.of())));

        return List.of(v1, v2, v3);
    }

    // -------------------------------------------------------------------------
    // Helper builders
    // -------------------------------------------------------------------------

    private EventEnvelope envelope(String actionId, String actionRootId) {
        return EventEnvelope.builder()
            .actionId(actionId)
            .actionRootId(actionRootId)
            .originUniqueEventId(UUID.randomUUID().toString())
            .originEventTimestamp(NOW)
            .correctorVersion(0)
            .processedCount(0)
            .build();
    }

    private GatewayEvent gatewayCapture(EventEnvelope env, double amount, String currency, String paymentId) {
        return gatewayEvent(env, "payment_captured", amount, currency, paymentId);
    }

    private GatewayEvent gatewayEvent(EventEnvelope env, String eventType, double amount, String currency, String paymentId) {
        return GatewayEvent.builder()
            .envelope(env)
            .paymentId(paymentId)
            .eventType(eventType)
            .processedOn(NOW)
            .amount(AmountValue.builder().value(amount).currencyCode(currency).build())
            .acquirerName("CKO_UK_LTD")
            .acquirerCountry("GB")
            .eventId(UUID.randomUUID().toString())
            .responseCode("10000")
            .build();
    }

    private CosEvent cosEvent(EventEnvelope env, double feeAmount, String currency,
            String feeType, boolean predicted, SchemeProfile scheme) {
        return CosEvent.builder()
            .envelope(env)
            .payload(CosEvent.CosPayload.builder()
                .fee(AmountValue.builder().value(feeAmount).currencyCode(currency).build())
                .isPredicted(predicted)
                .feeType(feeType.equals("INTERCHANGE_FEE") ? "FEE_TYPE_INTERCHANGE" : "FEE_TYPE_SCHEME")
                .direction("increase")
                .build())
            .metadata(CosEvent.CosMetadata.builder()
                .acquirerName("CKO_UK_LTD")
                .acquirerCompanyCkoLegalEntityCode("CKO_UK_LTD")
                .paymentMethod(scheme.paymentMethodCos())
                .build())
            .build();
    }

    private BalancesChangedEvent balancesCapture(EventEnvelope env, String clientId,
            String settlementEntity, String acquirerEntity,
            double holdingAmount, String holdingCcy,
            double processingAmount, String processingCcy,
            String valueDate, double reserveAmount, SchemeProfile scheme) {
        BalancesChangedEvent.BalancesChanges changes = BalancesChangedEvent.BalancesChanges.builder()
            .pending(BalancesChangedEvent.PendingChange.builder()
                .holdingAmount(AmountValue.builder().value(holdingAmount).currencyCode(holdingCcy).build())
                .processingAmount(AmountValue.builder().value(processingAmount).currencyCode(processingCcy).build())
                .build())
            .build();
        if (reserveAmount > 0) {
            changes.setRollingReserve(AmountValue.builder().value(reserveAmount).currencyCode(holdingCcy).build());
        }
        return BalancesChangedEvent.builder()
            .envelope(env)
            .metadata(BalancesChangedEvent.BalancesMetadata.builder()
                .clientId(clientId)
                .entityId(clientId)
                .settlementCompanyCkoLegalEntityCode(settlementEntity)
                .acquirerCompanyCkoLegalEntityCode(acquirerEntity)
                .schemeCode(scheme.schemeName().toUpperCase().replace(" ", "_"))
                .paymentMethod(scheme.paymentMethodBalances())
                .sourceEventType("payment_captured")
                .actionType("BALANCES_CHANGED_ACTION_TYPE_CAPTURE")
                .currencyAccountId("ca_" + clientId + "_" + holdingCcy.toLowerCase() + "_001")
                .acquirerName(acquirerEntity)
                .acquirerCountry(scheme.acquirerCountry())
                .clientSettlementType("Net")
                .valueDate(valueDate)
                .build())
            .actions(List.of(BalancesChangedEvent.BalancesAction.builder()
                .changes(changes)
                .actionMetadata(BalancesChangedEvent.ActionMetadata.builder()
                    .amountType("revenue")
                    .build())
                .build()))
            .build();
    }

    private SchemeSettlementEvent sdEvent(EventEnvelope env,
            double settlementAmount, String settlementCcy,
            double processingAmount, String processingCcy,
            SchemeProfile scheme, String txnType, String valueDate,
            String acquirerEntity, String settlementEntity, String clientId,
            List<SchemeSettlementEvent.SdFee> fees) {
        return SchemeSettlementEvent.builder()
            .envelope(env)
            .payload(SchemeSettlementEvent.SdPayload.builder()
                .settlementAmount(SchemeSettlementEvent.SignedAmount.builder()
                    .money(AmountValue.builder().value(settlementAmount).currencyCode(settlementCcy).build())
                    .sign("positive")
                    .build())
                .processingAmount(SchemeSettlementEvent.SignedAmount.builder()
                    .money(AmountValue.builder().value(processingAmount).currencyCode(processingCcy).build())
                    .sign("positive")
                    .build())
                .fees(fees)
                .build())
            .metadata(SchemeSettlementEvent.SdMetadata.builder()
                .scheme(scheme.schemeName())
                .transactionType(txnType)
                .expectedValueDate(valueDate)
                .centralProcessingDate(TODAY)
                .settlementServiceName(scheme.settlementServiceName())
                .settlementCountryCode(scheme.acquirerCountry())
                .acquirerCompanyCkoLegalEntityCode(acquirerEntity)
                .settlementCompanyCkoLegalEntityCode(settlementEntity)
                .acquirerName(acquirerEntity)
                .acquirerCountry(scheme.acquirerCountry())
                .entityId(clientId)
                .clientId(clientId)
                .build())
            .build();
    }

    private SchemeSettlementEvent.SdFee sdFee(String type, double amount, String currency) {
        return SchemeSettlementEvent.SdFee.builder()
            .type(type)
            .roundedAmount(amount)
            .currencyCode(currency)
            .sign("positive")
            .taxAmount(0)
            .build();
    }

    private CashEvent cashEvent(EventEnvelope env, double amount, String currency,
            String direction, String legalEntity, String entityId, String valueDate) {
        return CashEvent.builder()
            .envelope(env)
            .standardPayload(CashEvent.CashStandardPayload.builder()
                .amount(AmountValue.builder().value(amount).currencyCode(currency).build())
                .direction(direction)
                .build())
            .standardMetadata(CashEvent.CashStandardMetadata.builder()
                .scheme("VISA")
                .valueDate(valueDate)
                .legalEntity(legalEntity)
                .actionType("SETTLEMENT")
                .entityId(entityId)
                .currencyAccountId("ca_" + entityId + "_" + currency.toLowerCase() + "_001")
                .cashBatchId("batch_" + UUID.randomUUID().toString().substring(0, 8))
                .build())
            .build();
    }

    private LeLinkedTransaction copyWithVersion(LeLinkedTransaction src, int version) {
        return LeLinkedTransaction.builder()
            .id(UUID.randomUUID().toString())
            .actionId(src.getActionId())
            .actionRootId(src.getActionRootId())
            .transactionVersion(version)
            .gatewayEvents(new ArrayList<>(src.getGatewayEvents()))
            .balancesChangedEvents(new ArrayList<>(src.getBalancesChangedEvents()))
            .cosEvents(new ArrayList<>(src.getCosEvents()))
            .schemeSettlementEvents(new ArrayList<>(src.getSchemeSettlementEvents()))
            .cashEvents(new ArrayList<>(src.getCashEvents()))
            .build();
    }

    /** Rounds to 2 decimal places. */
    private static double r2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
```

- [ ] **Step 4: Run tests — expect all to pass**

```bash
cd le-simulator && ./gradlew test --tests "com.checkout.nexus.lesimulator.service.ScenarioLoaderTest"
```

Expected output: `42 tests completed, 0 failed`

- [ ] **Step 5: Commit**

```bash
cd le-simulator
git add src/main/java/com/checkout/nexus/lesimulator/service/ScenarioLoader.java \
        src/test/java/com/checkout/nexus/lesimulator/service/ScenarioLoaderTest.java
git commit -m "feat: fix pillar ordering, add settlement to all scenarios, add scheme parameterisation"
```

---

## Phase 4 — RandomSequenceEmitter

### Task 4: Create RandomSequenceEmitter + tests

**Files:**
- Create: `le-simulator/src/main/java/com/checkout/nexus/lesimulator/service/RandomSequenceEmitter.java`
- Create: `le-simulator/src/test/java/com/checkout/nexus/lesimulator/service/RandomSequenceEmitterTest.java`

- [ ] **Step 1: Write the failing test**

```java
// le-simulator/src/test/java/com/checkout/nexus/lesimulator/service/RandomSequenceEmitterTest.java
package com.checkout.nexus.lesimulator.service;

import com.checkout.nexus.lesimulator.config.LeBatchConfig;
import com.checkout.nexus.lesimulator.model.LeLinkedTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RandomSequenceEmitterTest {

    @Mock
    KafkaTemplate<String, LeLinkedTransaction> kafkaTemplate;

    @Mock
    LeBatchConfig config;

    @Spy
    ScenarioLoader scenarioLoader;

    @InjectMocks
    RandomSequenceEmitter emitter;

    @BeforeEach
    void setUp() {
        when(config.getTopicName()).thenReturn("test.topic");
    }

    @Test
    void emitSequence_sends4Messages() throws InterruptedException {
        emitter.emitSequence(0);
        verify(kafkaTemplate, times(4)).send(eq("test.topic"), anyString(), any(LeLinkedTransaction.class));
    }

    @Test
    void emitSequence_allMessagesShareSameActionId() throws InterruptedException {
        ArgumentCaptor<String> keyCaptor   = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LeLinkedTransaction> txnCaptor = ArgumentCaptor.forClass(LeLinkedTransaction.class);

        emitter.emitSequence(0);

        verify(kafkaTemplate, times(4)).send(eq("test.topic"), keyCaptor.capture(), txnCaptor.capture());

        String firstKey = keyCaptor.getAllValues().get(0);
        keyCaptor.getAllValues().forEach(k -> assertEquals(firstKey, k, "Kafka partition keys must all be the same action_id"));

        String firstActionId = txnCaptor.getAllValues().get(0).getActionId();
        txnCaptor.getAllValues().forEach(v -> assertEquals(firstActionId, v.getActionId()));
    }

    @Test
    void emitSequence_versionsAreMonotonicallyIncreasing() throws InterruptedException {
        ArgumentCaptor<LeLinkedTransaction> captor = ArgumentCaptor.forClass(LeLinkedTransaction.class);
        emitter.emitSequence(0);
        verify(kafkaTemplate, times(4)).send(anyString(), anyString(), captor.capture());
        List<LeLinkedTransaction> sent = captor.getAllValues();
        for (int i = 0; i < sent.size(); i++) {
            assertEquals(i + 1, sent.get(i).getTransactionVersion(),
                "Version " + (i + 1) + " was not in position " + i);
        }
    }

    @Test
    void emitSequence_v1HasOnlyGatewayEvents() throws InterruptedException {
        ArgumentCaptor<LeLinkedTransaction> captor = ArgumentCaptor.forClass(LeLinkedTransaction.class);
        emitter.emitSequence(0);
        verify(kafkaTemplate, times(4)).send(anyString(), anyString(), captor.capture());
        LeLinkedTransaction v1 = captor.getAllValues().get(0);
        assertFalse(v1.getGatewayEvents().isEmpty(),          "v1 must have GW");
        assertTrue(v1.getCosEvents().isEmpty(),               "v1 must not have COS");
        assertTrue(v1.getBalancesChangedEvents().isEmpty(),   "v1 must not have Balances");
        assertTrue(v1.getSchemeSettlementEvents().isEmpty(),  "v1 must not have SD");
    }

    @Test
    void emitSequence_lastVersionHasSchemeSettlement() throws InterruptedException {
        ArgumentCaptor<LeLinkedTransaction> captor = ArgumentCaptor.forClass(LeLinkedTransaction.class);
        emitter.emitSequence(0);
        verify(kafkaTemplate, times(4)).send(anyString(), anyString(), captor.capture());
        LeLinkedTransaction v4 = captor.getAllValues().get(3);
        assertFalse(v4.getSchemeSettlementEvents().isEmpty(), "v4 must be settled (has SD)");
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
cd le-simulator && ./gradlew test --tests "com.checkout.nexus.lesimulator.service.RandomSequenceEmitterTest" 2>&1 | tail -10
```

Expected: compilation error — `RandomSequenceEmitter` does not exist.

- [ ] **Step 3: Create RandomSequenceEmitter.java**

```java
// le-simulator/src/main/java/com/checkout/nexus/lesimulator/service/RandomSequenceEmitter.java
package com.checkout.nexus.lesimulator.service;

import com.checkout.nexus.lesimulator.config.LeBatchConfig;
import com.checkout.nexus.lesimulator.model.LeLinkedTransaction;
import com.checkout.nexus.lesimulator.model.SchemeProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class RandomSequenceEmitter {

    private final KafkaTemplate<String, LeLinkedTransaction> kafkaTemplate;
    private final LeBatchConfig config;
    private final ScenarioLoader scenarioLoader;

    private static final Random RANDOM = new Random();

    private static final String[] CLIENT_IDS = {
        "cli_acme_corp", "cli_global_ltd", "cli_retail_eu", "cli_fintech_uk"
    };

    /**
     * Emits a complete v1→v4 capture sequence for a randomly selected scheme,
     * amount (10–1000 in the scheme's default currency), and client entity.
     * Waits {@code delayMs} between each version.
     */
    public void emitSequence(int delayMs) throws InterruptedException {
        SchemeProfile scheme  = pickScheme();
        double amount         = Math.round((10 + RANDOM.nextDouble() * 990) * 100.0) / 100.0;
        String clientId       = CLIENT_IDS[RANDOM.nextInt(CLIENT_IDS.length)];

        List<LeLinkedTransaction> versions = scenarioLoader.buildRandomCaptureSequence(scheme, amount, clientId);

        for (int i = 0; i < versions.size(); i++) {
            LeLinkedTransaction version = versions.get(i);
            log.info("Producing random LE transaction: actionId={}, version={}, scheme={}, amount={} {}",
                version.getActionId(), version.getTransactionVersion(),
                scheme.schemeName(), amount, scheme.defaultCurrency());
            kafkaTemplate.send(config.getTopicName(), version.getActionId(), version);
            if (delayMs > 0 && i < versions.size() - 1) {
                Thread.sleep(delayMs);
            }
        }
    }

    private SchemeProfile pickScheme() {
        double r = RANDOM.nextDouble();
        double cumulative = 0;
        List<SchemeProfile> all = SchemeProfile.ALL;
        for (int i = 0; i < all.size(); i++) {
            cumulative += SchemeProfile.VOLUME_WEIGHTS[i];
            if (r < cumulative) {
                return all.get(i);
            }
        }
        return SchemeProfile.VISA;
    }
}
```

- [ ] **Step 4: Run tests — expect all to pass**

```bash
cd le-simulator && ./gradlew test --tests "com.checkout.nexus.lesimulator.service.RandomSequenceEmitterTest"
```

Expected output: `5 tests completed, 0 failed`

- [ ] **Step 5: Commit**

```bash
cd le-simulator
git add src/main/java/com/checkout/nexus/lesimulator/service/RandomSequenceEmitter.java \
        src/test/java/com/checkout/nexus/lesimulator/service/RandomSequenceEmitterTest.java
git commit -m "feat: add RandomSequenceEmitter for proper v1→v4 sequential emission"
```

---

## Phase 5 — Wire Up Service and Controller

### Task 5: Update SimulatorService and SimulatorController

**Files:**
- Modify: `le-simulator/src/main/java/com/checkout/nexus/lesimulator/service/SimulatorService.java`
- Modify: `le-simulator/src/main/java/com/checkout/nexus/lesimulator/controller/SimulatorController.java`

- [ ] **Step 1: Replace SimulatorService.java**

```java
// le-simulator/src/main/java/com/checkout/nexus/lesimulator/service/SimulatorService.java
package com.checkout.nexus.lesimulator.service;

import com.checkout.nexus.lesimulator.config.LeBatchConfig;
import com.checkout.nexus.lesimulator.model.LeLinkedTransaction;
import com.checkout.nexus.lesimulator.model.SchemeProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulatorService {

    private final KafkaTemplate<String, LeLinkedTransaction> kafkaTemplate;
    private final LeBatchConfig config;
    private final ScenarioLoader scenarioLoader;
    private final RandomSequenceEmitter randomSequenceEmitter;

    private TaskScheduler taskScheduler;
    private ScheduledFuture<?> randomFuture;
    private final AtomicReference<String> mode             = new AtomicReference<>("idle");
    private final AtomicReference<String> currentScenario  = new AtomicReference<>(null);
    private final AtomicInteger messagesProduced            = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("simulator-");
        scheduler.initialize();
        this.taskScheduler = scheduler;
    }

    public void playScenario(String scenarioId, int delayMs, SchemeProfile scheme) {
        if (!"idle".equals(mode.get())) {
            throw new IllegalStateException("Simulator is already running in mode: " + mode.get());
        }
        mode.set("scenario");
        currentScenario.set(scenarioId);

        taskScheduler.schedule(() -> {
            try {
                List<LeLinkedTransaction> versions = scenarioLoader.loadScenario(scenarioId, scheme);
                for (LeLinkedTransaction version : versions) {
                    log.info("Producing LE transaction: actionId={}, version={}, scheme={}",
                        version.getActionId(), version.getTransactionVersion(), scheme.schemeName());
                    kafkaTemplate.send(config.getTopicName(), version.getActionId(), version);
                    messagesProduced.incrementAndGet();
                    if (delayMs > 0) {
                        Thread.sleep(delayMs);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error playing scenario {}", scenarioId, e);
            } finally {
                mode.set("idle");
                currentScenario.set(null);
            }
        }, java.time.Instant.now());
    }

    /**
     * Starts random mode: every {@code intervalMs} ms, emits a full v1→v4 capture
     * sequence via {@link RandomSequenceEmitter}.
     * The default inter-version delay within each sequence is 500 ms.
     */
    public void startRandom(int intervalMs) {
        if (!"idle".equals(mode.get())) {
            throw new IllegalStateException("Simulator is already running in mode: " + mode.get());
        }
        mode.set("random");

        randomFuture = taskScheduler.scheduleAtFixedRate(() -> {
            try {
                randomSequenceEmitter.emitSequence(500);
                messagesProduced.addAndGet(4);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error in random sequence generation", e);
            }
        }, Duration.ofMillis(intervalMs));
    }

    public void stopRandom() {
        if (randomFuture != null) {
            randomFuture.cancel(false);
            randomFuture = null;
        }
        mode.set("idle");
    }

    public Map<String, Object> getStatus() {
        return Map.of(
            "mode", mode.get(),
            "currentScenario", currentScenario.get() != null ? currentScenario.get() : "",
            "messagesProduced", messagesProduced.get()
        );
    }
}
```

- [ ] **Step 2: Replace SimulatorController.java**

```java
// le-simulator/src/main/java/com/checkout/nexus/lesimulator/controller/SimulatorController.java
package com.checkout.nexus.lesimulator.controller;

import com.checkout.nexus.lesimulator.model.SchemeProfile;
import com.checkout.nexus.lesimulator.service.ScenarioLoader;
import com.checkout.nexus.lesimulator.service.SimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/simulate")
@RequiredArgsConstructor
public class SimulatorController {

    private final SimulatorService simulatorService;
    private final ScenarioLoader scenarioLoader;

    @GetMapping("/scenarios")
    public List<ScenarioLoader.ScenarioInfo> listScenarios() {
        return scenarioLoader.listScenarios();
    }

    /**
     * Plays a named scenario.
     *
     * Request body (all optional):
     * <pre>{ "delayMs": 500, "scheme": "Carte Bancaire" }</pre>
     *
     * {@code scheme} must match a {@link SchemeProfile#schemeName()} (case-insensitive).
     * Defaults to Visa if omitted or unrecognised.
     */
    @PostMapping("/scenario/{id}")
    public ResponseEntity<Map<String, String>> playScenario(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        int delayMs    = body != null && body.containsKey("delayMs")
            ? ((Number) body.get("delayMs")).intValue() : 500;
        String schemeName = body != null ? (String) body.get("scheme") : null;
        SchemeProfile scheme = resolveScheme(schemeName);
        simulatorService.playScenario(id, delayMs, scheme);
        return ResponseEntity.ok(Map.of("status", "started", "scenarioId", id, "scheme", scheme.schemeName()));
    }

    @PostMapping("/random/start")
    public ResponseEntity<Map<String, String>> startRandom(
            @RequestBody(required = false) Map<String, Integer> body) {
        int intervalMs = (body != null && body.containsKey("intervalMs")) ? body.get("intervalMs") : 2000;
        simulatorService.startRandom(intervalMs);
        return ResponseEntity.ok(Map.of("status", "random_started"));
    }

    @PostMapping("/random/stop")
    public ResponseEntity<Map<String, String>> stopRandom() {
        simulatorService.stopRandom();
        return ResponseEntity.ok(Map.of("status", "random_stopped"));
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return simulatorService.getStatus();
    }

    /** Returns all supported scheme profiles so callers know valid scheme names. */
    @GetMapping("/schemes")
    public List<Map<String, String>> listSchemes() {
        return SchemeProfile.ALL.stream()
            .map(p -> Map.of("schemeName", p.schemeName(), "defaultCurrency", p.defaultCurrency()))
            .toList();
    }

    private SchemeProfile resolveScheme(String name) {
        if (name == null) return SchemeProfile.VISA;
        return SchemeProfile.ALL.stream()
            .filter(p -> p.schemeName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(SchemeProfile.VISA);
    }
}
```

- [ ] **Step 3: Build the full service**

```bash
cd le-simulator && ./gradlew build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run all tests**

```bash
cd le-simulator && ./gradlew test
```

Expected: all tests pass, 0 failures.

- [ ] **Step 5: Commit**

```bash
cd le-simulator
git add src/main/java/com/checkout/nexus/lesimulator/service/SimulatorService.java \
        src/main/java/com/checkout/nexus/lesimulator/controller/SimulatorController.java
git commit -m "feat: wire RandomSequenceEmitter into service, add scheme param to controller"
```

---

## Phase 6 — Research Docs + SchemeProfile Constants (after BQ results available)

> These tasks require the BQ query results from Task 1 to be filled in `production-distributions.md`.

### Task 6: Update research docs

**Files:**
- Modify: `projects/Nexus/research/knowledge.md`
- Modify: `projects/Nexus/research/linking_engine_contract.md` (only if Q3 or Q10 require corrections)

- [ ] **Step 1: Add pointer in knowledge.md**

In `research/knowledge.md`, find the `## Linking Engine Data Contract` section and add a line after the existing bullet:

```markdown
- `research/le/production-distributions.md` — Production volume distributions for schemes, currencies, fees, pillar arrival ordering, and settlement service names. Derived from BQ queries against the `linkedtransaction` table.
```

- [ ] **Step 2: Update linking_engine_contract.md if Q3 found ordering deviations**

If the Q3 results show a pattern other than `GW→COS→BAL→SD` as dominant (>90%), update the "Versioning Behaviour" section of `linking_engine_contract.md`:

Find this line in `research/linking_engine_contract.md`:
```
v2: COS
v3: Balances / FIAPI
```

And update it to match the dominant pattern found. If `GW→COS→BAL→SD` is confirmed as dominant, no change needed.

- [ ] **Step 3: Update linking_engine_contract.md if Q10 found casing is pillar-specific**

If Q10 shows that `payment_method` casing is consistent per-pillar (e.g. Balances always uppercase, COS always lowercase), add a note to the `payment_method` field description in the contract doc. Find:

```
| `payment_method` | `VISA`, `MASTERCARD`, `visa`, `mastercard` (case inconsistent in data) |
```

And update the description with the actual finding from Q10.

- [ ] **Step 4: Commit**

```bash
cd projects/Nexus
git add research/knowledge.md research/linking_engine_contract.md
git commit -m "docs: add production-distributions pointer, update LE contract from BQ findings"
```

---

### Task 7: Fill SchemeProfile constants from BQ results

**Files:**
- Modify: `le-simulator/src/main/java/com/checkout/nexus/lesimulator/model/SchemeProfile.java`

- [ ] **Step 1: Update each constant using BQ results**

For each `// TODO: BQ Qn` comment, replace the placeholder value with the value from `production-distributions.md`:

| Field | Source query | What to use |
|-------|-------------|-------------|
| `paymentMethodBalances` for each scheme | Q10 `pm_balances` column | The most common value for that scheme |
| `paymentMethodCos` for each scheme | Q10 `pm_cos` column | The most common value for that scheme |
| `defaultCurrency` for each scheme | Q7 top currency by `pct_within_scheme` | Pick the dominant currency |
| `interchangeFeeRate` | Q4 `avg_ic_fee_pct / 100` | Use the scheme+currency row with largest `sample_size` |
| `schemeFeeRate` | Q4 `avg_scheme_fee_pct / 100` | Same row |
| `settlementServiceName` | Q9 top service per scheme | Pick the service with highest `pct_within_scheme` |
| `acquirerCountry` | Q6 for minority schemes; keep "GB" for Visa/MC unless Q7 shows otherwise | |
| `VOLUME_WEIGHTS` | Q1a `unique_action_ids` per scheme | Normalise to sum 1.0 |

For Carte Bancaire `schemeName`: use the exact string from Q6's distinct scheme list query result.

- [ ] **Step 2: Remove all `// TODO: BQ` comments once values are confirmed**

- [ ] **Step 3: Run SchemeProfile tests**

```bash
cd le-simulator && ./gradlew test --tests "com.checkout.nexus.lesimulator.model.SchemeProfileTest"
```

Expected: `4 tests completed, 0 failed`

- [ ] **Step 4: Run full test suite**

```bash
cd le-simulator && ./gradlew test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
cd le-simulator
git add src/main/java/com/checkout/nexus/lesimulator/model/SchemeProfile.java
git commit -m "feat: update SchemeProfile constants with production BQ data"
```
