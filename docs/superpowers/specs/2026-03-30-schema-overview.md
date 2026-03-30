# Schema Overview Design

**Date:** 2026-03-30
**Status:** Approved

## Summary

Add an "Overview" node as the first item in the SchemaExplorer sidebar. When selected, the right panel renders a visual A2-style layout: nested containment diagram on the left, stacked callout cards on the right. All existing field-table behaviour for other nodes is unchanged.

---

## Changes

### File: `ui/src/pages/learn/SchemaExplorer.tsx`

Only this file changes. No new files, no routing changes.

---

## Sidebar

`'Overview'` is prepended to `NODE_NAMES` and the `NodeName` union type:

```ts
type NodeName = 'Overview' | 'Block' | 'Transaction' | 'Transaction Metadata' | 'Leg' | 'Party' | 'Fee'
const NODE_NAMES: NodeName[] = ['Overview', 'Block', 'Transaction', 'Transaction Metadata', 'Leg', 'Party', 'Fee']
```

`useState` defaults to `'Overview'`.

---

## Right panel

When `active === 'Overview'`, render `<OverviewPanel />` instead of the field table. All other nodes continue to render the field table as before.

---

## OverviewPanel layout (A2)

Two columns side by side (`flex gap-6`):

**Left — nested boxes diagram**

Concentric bordered boxes. Outer to inner: Block → Transaction → Leg → (Party + Fee side by side). Each box shows its label and 2–4 key field names in muted text below the label. Metadata shown as a smaller muted box inside Transaction alongside Leg.

```
┌─ BLOCK (cyan) ─────────────────────────────┐
│  nexus_id · action_id · status · entity    │
│  ┌─ TRANSACTION [] (indigo) ─────────────┐  │
│  │  product_type · transaction_type      │  │
│  │  ┌─ LEG [] (emerald) ──────────────┐  │  │
│  │  │  leg_type · amount · currency    │  │  │
│  │  │  ┌─ PARTY ×2 ─┐  ┌─ FEE [] ──┐ │  │  │
│  │  │  │  amber      │  │  rose     │ │  │  │
│  │  │  └────────────┘  └───────────┘ │  │  │
│  │  └─────────────────────────────────┘  │  │
│  │  ┌─ metadata ─────────────────────┐   │  │
│  │  │  scheme_code · acquirer_name…  │   │  │
│  │  └────────────────────────────────┘   │  │
│  └────────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

**Right — stacked callout cards**

Five cards, one per level, in top-to-bottom order. Each card:
- `border-l-2 border-{color}-400 bg-{color}-50 rounded-r px-3 py-2`
- Bold level name in `text-{color}-700 text-sm font-semibold`
- One-sentence description in `text-zinc-600 text-xs mt-0.5`

| Level | Color | Description |
|---|---|---|
| Block | cyan | One per `action_id`. The envelope for the whole payment lifecycle. |
| Transaction | indigo | One per product type (ACQUIRING, PAYOUT…). Classifies the trade family. |
| Leg | emerald | A money movement: `from_party → to_party` with amount, currency, and status. |
| Party | amber | An account endpoint: entity, bank account, or scheme participant. |
| Fee | rose | A cost attached to a leg (interchange, scheme fee, processing fee…). |

---

## Color system

| Level | Border/bg classes |
|---|---|
| Block | `border-cyan-300 bg-cyan-50`, label `text-cyan-700` |
| Transaction | `border-indigo-300 bg-indigo-50`, label `text-indigo-700` |
| Leg | `border-emerald-300 bg-emerald-50`, label `text-emerald-700` |
| Party | `border-amber-300 bg-amber-50`, label `text-amber-700` |
| Fee | `border-rose-300 bg-rose-50`, label `text-rose-700` |
| Metadata | `border-zinc-200 bg-white`, label `text-zinc-400` |

---

## Out of scope

- Interactivity on the diagram (clicking a box to navigate to that node's field table — future enhancement)
- Any changes to other Learn pages
