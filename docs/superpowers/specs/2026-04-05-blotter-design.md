# Blotter Design Spec

## Overview

A new Blotter page in the Nexus POC that provides visibility into the double-entry ledger postings produced by the rules engine. Two primary views — a flat entry list and a grouped journal — plus the existing posting errors absorbed as a third tab. Full traceability from any ledger entry back through the Nexus Block to the original LE message.

## Page Location & Navigation

- Route: `/rules-engine/blotter` (tabs addressable via `/blotter/journal`, `/blotter/errors`)
- Sits in the Rules Engine nav section, replacing the current Posting Errors link
- `/rules-engine/errors` redirects to `/rules-engine/blotter?tab=errors`

## Tabs

### Entries

Flat, sortable table of all `LedgerEntry` rows.

Columns: timestamp, nexus ID, transaction ID, rule name, account, side (DR/CR), amount, currency, product type, transaction type.

- DR rows styled amber, CR rows styled green
- Sortable by clicking column headers (default: newest first)
- Row click opens the side detail panel

### Journal

Entries grouped by `nexus_id`, with collapsible sub-groups by `rule_id`.

Each nexus group header shows: nexus ID (clickable), timestamp, entry count, total debits, total credits, balanced indicator (green checkmark / red X).

Each rule sub-group shows the rule name and its DR/CR pair(s) indented beneath.

Most recent transaction expanded by default; rest collapsed.

### Errors

Posting errors from `GET /ledger/errors`. Same data as the current Posting Errors page: nexus ID, transaction ID, currency, debit total, credit total, rules fired, timestamp.

Row click opens side panel with error detail.

## Filter Bar

Shared across all tabs. Sits below the tab bar.

Controls:
- Date range picker
- Account dropdown (populated from `GET /accounts`)
- Currency dropdown
- Product type dropdown
- Side dropdown (DR / CR)
- Nexus ID text search

All filtering is client-side against the React Query cache.

## Side Detail Panel

Right-aligned panel, ~350px wide. Opens on row click, overlays the table (does not push content).

Shows all fields of the selected entry or error. Traceability section at the bottom with two links:
- "View Nexus Block" → opens the trace drawer
- "View LE Message" → opens the trace drawer in LE view

Close via X button or clicking the same row again. Clicking a different row swaps content.

## Trace Drawer

Full-height overlay from the right, ~60% viewport width. Dark backdrop, dismissible via X or backdrop click.

Two views toggled by a breadcrumb at the top:
- **Nexus Block** — renders the existing `TransactionTrace` component with the full block structure
- **LE Message** — read-only Monaco editor (JSON, dark theme) showing the raw LE event

## New Entries Notification

WebSocket connection to `ws://rules-engine:8080/ws`, listening for `nexus.ledger.entries` messages.

Counts new entries since last data fetch. When count > 0, a banner slides in below the filter bar: "N new entries available" with a refresh icon. Clicking refetches data via React Query `invalidateQueries` and resets the counter.

Does not inject entries into the table directly — always a full refetch on user action.

## New Components

| Component | Purpose |
|---|---|
| `BlotterPage` | Top-level page. Tab state, filter state, WebSocket subscription |
| `BlotterFilterBar` | Shared filter controls across tabs |
| `EntryTable` | Flat entries tab with sortable columns |
| `JournalView` | Grouped accordion view (nexus ID → rule) |
| `ErrorList` | Posting errors tab (replaces standalone page) |
| `EntryDetailPanel` | Side panel with entry fields + traceability links |
| `TraceDrawer` | Drawer overlay with TransactionTrace / Monaco views |
| `NewEntriesBanner` | Sticky notification banner for new entries |

No new shared utility components. Reuses existing: `TransactionTrace`, `StatusBadge`, `Toast`, `Layout`.

## API Surface

### Existing endpoints (no changes)

- `GET /ledger/entries` — all ledger entries (supports `?nexusId=`)
- `GET /ledger/errors` — all posting errors
- `GET /rules` — rule name lookups
- `GET /accounts` — account list for filter dropdown

### New backend endpoints

- `GET /blocks/{nexusId}` — single Nexus block by nexus ID. The `nexus_blocks` table already stores the raw JSON; this exposes it via REST.
- `GET /blocks/{nexusId}/source` — original LE message that produced this block. Requires a small change: the Nexus Transformer must forward the original LE JSON as a field in the Nexus block message it publishes to `nexus.blocks`, so the rules engine can persist it alongside the block. The rules engine stores this in a new `source_event` text column on the `nexus_blocks` table. Returns the raw LE JSON.

### WebSocket

Existing `ws://rules-engine:8080/ws` — no changes. The blotter subscribes to `nexus.ledger.entries` topic messages for the new-entries counter.

## Data Flow

1. Page loads → React Query fetches entries, errors, accounts, rules
2. User applies filters → client-side filtering on cached data
3. User clicks row → side panel opens with entry detail
4. User clicks "View Nexus Block" → `GET /blocks/{nexusId}` → drawer opens with TransactionTrace
5. User clicks "LE Message" breadcrumb → `GET /blocks/{nexusId}/source` → drawer swaps to Monaco
6. WebSocket receives new entry → counter increments → banner appears
7. User clicks banner → `invalidateQueries` → fresh data, counter resets

## Styling

- Dark theme, consistent with existing POC UI (Tailwind)
- Tab bar: underline active tab, red badge on Errors tab when errors exist
- Table: alternating row backgrounds, amber DR / green CR text
- Selected row: indigo left border accent
- Side panel: right overlay with shadow, 350px
- Drawer: right overlay 60% width, dark backdrop
- Banner: indigo background, white text, slide-down animation
- Journal accordions: chevron expand/collapse, balanced status icon in header

## Scope Exclusions

- No server-side pagination or filtering (client-side is sufficient for POC volumes)
- No account ledger / trial balance view (planned for later)
- No export (CSV, etc.)
- No entry editing or deletion
