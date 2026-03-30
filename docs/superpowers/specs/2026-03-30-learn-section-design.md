# Learn Section Design

**Date:** 2026-03-30
**Status:** Approved

## Summary

Add a top-level "Learn" section to the Nexus POC UI — an educational area that lets demo viewers understand the Nexus schema, see worked transaction examples, and understand how Nexus differs from the Linking Engine. Features are ported from the original static HTML prototype (`ui/index.html` in the Nexus repo, commit `cddb227`).

---

## Navigation

"Learn" becomes a top-level nav item alongside Dashboard, Live, Config, DLQ. It has three sub-pages accessible via a sub-nav inside the Learn shell:

- **Schema** (default) — `/learn/schema`
- **Examples** — `/learn/examples`
- **LE vs Nexus** — `/learn/le-vs-nexus`

`/learn` redirects to `/learn/schema`.

---

## Architecture

### New files

```
src/pages/learn/
  LearnLayout.tsx        — Shell with sub-nav tabs + <Outlet />
  SchemaExplorer.tsx     — Schema tree + field table
  ExamplesPage.tsx       — Scenario list + LE/Nexus split view
  LEvsNexus.tsx          — Narrative + field mapping table
src/data/learnExamples.ts — 9 hardcoded LE event payloads with metadata
```

### Routing changes (App.tsx)

```
/learn                  → redirect to /learn/schema
/learn/schema           → SchemaExplorer
/learn/examples         → ExamplesPage
/learn/le-vs-nexus      → LEvsNexus
```

All four routes nested inside `LearnLayout`, which itself is nested inside the existing `Layout` shell.

### Reused without changes

- `TransactionTrace` component — renders the Nexus output on the Examples page
- `runTestBench` API function — fires the transformer call on the Examples page
- `StatusBadge` component
- `schema/nexus.schema.json` — vendored schema, imported at build time

---

## Sub-page: Schema Explorer

**Left sidebar:** Tree navigation with 6 nodes:
`Transaction → Trade → TradeMetadata → Leg → Party → Fee`

Clicking a node replaces the right panel. Active node highlighted with cyan left-border accent (matching existing UI style).

**Right panel:** Field table for the selected node.

| Column | Source |
|--------|--------|
| Field name | `properties` key |
| Type | `type` / `enum` / `$ref` |
| Required | Present in `required[]` array |
| Description | `description` property |
| LE Source | `x-le-source` annotation (omitted if absent) |

Data source: `import schema from '../../../schema/nexus.schema.json'` — no backend call. Always available.

---

## Sub-page: Examples

**Left sidebar:** List of 9 curated scenarios. Each entry shows a title and subtitle. Clicking selects the scenario and fires the transformer.

**Right panel — side-by-side split:**

| Left column | Right column |
|-------------|--------------|
| "LE Event In" | "Nexus Block Out" |
| Formatted JSON (`<pre>`) with a collapse/expand toggle | `TransactionTrace` component |
| The hardcoded LE payload from `learnExamples.ts` | Result of `runTestBench(payload)` |

On scenario select: show loading state in the Nexus column while the API call is in-flight. On error: show inline error message in the Nexus column. The LE column renders immediately (static data, no wait).

**The 9 scenarios** (LE event payloads ported from the static prototype):

1. Simple VISA Capture — basic acquiring capture with PREDICTED legs
2. Cross-Currency Capture — USD trade settled in EUR with ACTUAL legs
3. Cross-Entity Capture — acquirer (FR) differs from funding entity (UK) with TRANSFER trade
4. Cross-Currency + Cross-Entity — USD trade, EUR settlement, cross-entity TRANSFER
5. Refund — partial refund with reversed fund flow
6. Chargeback — chargeback with reversed settlement and funding
7. Payout — client payout with processing fee
8. Topup — client top-up (funds flow client → CKO)
9. Cash-Matched Settlement — standalone cash transaction for scheme batch settlement

Each entry in `learnExamples.ts`:
```ts
export interface LearnExample {
  id: string
  title: string
  subtitle: string
  lePayload: unknown  // LE event object passed directly to runTestBench
}
```

---

## Sub-page: LE vs Nexus

Single scrollable page. Two sections:

### Section 1 — Narrative (key conceptual differences)

6 callout cards, each covering one principle:

1. **LE is cumulative, Nexus reads latest** — LE emits a new full record each time a pillar arrives. Nexus always reads the latest `transaction_version` for a given `action_id`.
2. **One `action_id` = one Nexus block** — Nexus is not a ledger and does not track balances. It describes financial events.
3. **Partial state is normal** — Pillars arrive in order (GW → COS → FIAPI → SD → Cash), but not all at once. Legs are added incrementally as data arrives.
4. **PREDICTED vs ACTUAL** — COS-sourced legs are `PREDICTED`; SD-sourced legs are `ACTUAL`.
5. **LE internals not surfaced** — Fields like `transaction_version`, `transaction_state_id`, `metadata`, `key` are LE operational concepts. Nexus does not expose them.
6. **`status` is computed** — `NOT_LIVE / LIVE / DEAD` is determined by Nexus from data completeness. It does not come from LE.

### Section 2 — Field mapping table

Grouped by LE pillar. Columns: LE Field | Nexus Field | Notes.

Pillars covered: Top-level, Gateway, COS, FIAPI, Scheme Settlement, Cash.

Fields that are not surfaced in Nexus shown in muted style with "(not surfaced)" in the Nexus Field column.

Source: content from `schema/le_nexus_mapping.md`, hardcoded as data in `LEvsNexus.tsx` (not parsed from the `.md` file at runtime). The mapping doc is the reference; the component owns the rendered representation.

---

## Out of scope

- Cross-linking from Schema Explorer fields to relevant Examples (future enhancement)
- Live backend dependency for the Schema or LE vs Nexus pages (both are static)
- Any changes to existing pages (Test Bench, Dashboard, Config, DLQ, Live)
