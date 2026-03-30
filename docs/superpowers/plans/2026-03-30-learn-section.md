# Learn Section Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a top-level "Learn" section to the Nexus POC UI with three sub-pages: Schema Explorer, Examples (live transformer), and LE vs Nexus comparison.

**Architecture:** `LearnLayout` acts as a shell with a sub-nav and `<Outlet />`. Three child pages are mounted under `/learn/*` and nested inside the existing `Layout`. Learn pages break out of `Layout`'s `p-6` wrapper using `-m-6` to achieve full-height sidebar layouts. The Examples page fires the existing `runTestBench` API call with hardcoded LE payloads and renders the result in the existing `TransactionTrace` component.

**Tech Stack:** React 18, React Router v6, TypeScript, Tailwind CSS, `@tanstack/react-query`, Vite JSON imports.

**Spec:** `docs/superpowers/specs/2026-03-30-learn-section-design.md`

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `ui/tsconfig.app.json` | Modify | Add `resolveJsonModule: true` for JSON schema import |
| `ui/src/data/learnExamples.ts` | Create | 9 LE event payloads with title/subtitle metadata |
| `ui/src/components/Layout.tsx` | Modify | Add "Learn" to `NAV_LINKS` |
| `ui/src/App.tsx` | Modify | Add `/learn/*` routes |
| `ui/src/pages/learn/LearnLayout.tsx` | Create | Sub-nav shell with `<Outlet />` |
| `ui/src/pages/learn/SchemaExplorer.tsx` | Create | Schema tree + field table from vendored JSON |
| `ui/src/pages/learn/ExamplesPage.tsx` | Create | Side-by-side LE input / Nexus output |
| `ui/src/pages/learn/LEvsNexus.tsx` | Create | Narrative cards + field mapping table |

---

## Task 1: Enable JSON imports + create LE payload data

**Files:**
- Modify: `ui/tsconfig.app.json`
- Create: `ui/src/data/learnExamples.ts`

- [ ] **Step 1: Add `resolveJsonModule` to tsconfig**

Open `ui/tsconfig.app.json`. The current `compilerOptions` are:
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "forceConsistentCasingInFileNames": true
  },
  "include": ["src"]
}
```

Add `"resolveJsonModule": true` after `"forceConsistentCasingInFileNames"`:
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "forceConsistentCasingInFileNames": true,
    "resolveJsonModule": true
  },
  "include": ["src"]
}
```

- [ ] **Step 2: Create `ui/src/data/learnExamples.ts`**

```typescript
export interface LearnExample {
  id: string
  title: string
  subtitle: string
  lePayload: unknown
}

export const LEARN_EXAMPLES: LearnExample[] = [
  {
    id: 'simple-capture',
    title: 'Simple Capture',
    subtitle: 'GW-only → NOT_LIVE, FUNDING leg predicted',
    lePayload: {
      id: 'le-001',
      actionId: 'act_cap_001',
      actionRootId: 'pay_001',
      transactionVersion: 1,
      gatewayEvents: [
        {
          eventType: 'payment_captured',
          processedOn: '2026-03-20T14:30:00Z',
          amount: { value: 100.0, currencyCode: 'EUR' },
          acquirerName: 'CKO_UK_LTD',
          acquirerCountry: 'GB',
        },
      ],
      balancesChangedEvents: [],
      cosEvents: [],
      schemeSettlementEvents: [],
      cashEvents: [],
    },
  },
  {
    id: 'settled-capture',
    title: 'Settled Capture',
    subtitle: 'GW + SD → LIVE/SETTLED, SCHEME_SETTLEMENT + FUNDING legs',
    lePayload: {
      id: 'le-002',
      actionId: 'act_cap_002',
      actionRootId: 'pay_002',
      transactionVersion: 2,
      gatewayEvents: [
        {
          eventType: 'payment_captured',
          processedOn: '2026-03-18T10:15:00Z',
          amount: { value: 200.0, currencyCode: 'EUR' },
          acquirerName: 'CKO_UK_LTD',
          acquirerCountry: 'GB',
        },
      ],
      balancesChangedEvents: [],
      cosEvents: [],
      schemeSettlementEvents: [
        {
          payload: {
            settlementAmount: {
              money: { value: 200.0, currencyCode: 'EUR' },
              sign: 'CREDIT',
            },
            fees: [
              { type: 'INTERCHANGE_FEE', roundedAmount: 0.55, currencyCode: 'EUR', sign: 'DEBIT' },
              { type: 'SCHEME_FEE', roundedAmount: 0.12, currencyCode: 'EUR', sign: 'DEBIT' },
            ],
          },
          metadata: {
            scheme: 'VISA',
            transactionType: 'Capture',
            clientId: 'cli_acme_corp',
            settlementCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            acquirerCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            settlementServiceName: 'ISS',
            settlementCountryCode: 'GB',
            centralProcessingDate: '2026-03-19',
            expectedValueDate: '2026-03-21',
          },
        },
      ],
      cashEvents: [],
    },
  },
  {
    id: 'cross-currency',
    title: 'Cross-Currency Capture',
    subtitle: 'USD captured, EUR settled — currencies differ between GW and SD',
    lePayload: {
      id: 'le-003',
      actionId: 'act_cap_003',
      actionRootId: 'pay_003',
      transactionVersion: 2,
      gatewayEvents: [
        {
          eventType: 'payment_captured',
          processedOn: '2026-03-19T09:00:00Z',
          amount: { value: 100.0, currencyCode: 'USD' },
          acquirerName: 'CKO_UK_LTD',
          acquirerCountry: 'GB',
        },
      ],
      balancesChangedEvents: [],
      cosEvents: [],
      schemeSettlementEvents: [
        {
          payload: {
            settlementAmount: {
              money: { value: 92.0, currencyCode: 'EUR' },
              sign: 'CREDIT',
            },
            fees: [
              { type: 'INTERCHANGE_FEE', roundedAmount: 0.46, currencyCode: 'EUR', sign: 'DEBIT' },
            ],
          },
          metadata: {
            scheme: 'MASTERCARD',
            transactionType: 'Capture',
            clientId: 'cli_acme_corp',
            settlementCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            acquirerCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            expectedValueDate: '2026-03-21',
          },
        },
      ],
      cashEvents: [],
    },
  },
  {
    id: 'cos-stage',
    title: 'Capture with COS Fees',
    subtitle: 'GW + COS → NOT_LIVE, predicted fees visible before settlement',
    lePayload: {
      id: 'le-004',
      actionId: 'act_cap_004',
      actionRootId: 'pay_004',
      transactionVersion: 2,
      gatewayEvents: [
        {
          eventType: 'payment_captured',
          processedOn: '2026-03-20T11:00:00Z',
          amount: { value: 500.0, currencyCode: 'GBP' },
          acquirerName: 'CKO_UK_LTD',
          acquirerCountry: 'GB',
        },
      ],
      balancesChangedEvents: [],
      cosEvents: [
        {
          payload: {
            fee: { value: 1.25, currencyCode: 'GBP' },
            isPredicted: true,
            feeType: 'FEE_TYPE_INTERCHANGE',
            direction: 'DEBIT',
          },
          metadata: {
            acquirerName: 'CKO_UK_LTD',
            acquirerCountry: 'GB',
            acquirerCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            reconciliationReference: 'RECON-004',
          },
        },
      ],
      schemeSettlementEvents: [],
      cashEvents: [],
    },
  },
  {
    id: 'refund',
    title: 'Refund',
    subtitle: 'payment_refunded → ACQUIRING/REFUND, reversed fund flow',
    lePayload: {
      id: 'le-005',
      actionId: 'act_ref_001',
      actionRootId: 'pay_001',
      transactionVersion: 1,
      gatewayEvents: [
        {
          eventType: 'payment_refunded',
          processedOn: '2026-03-21T10:00:00Z',
          amount: { value: 50.0, currencyCode: 'EUR' },
          acquirerName: 'CKO_UK_LTD',
          acquirerCountry: 'GB',
        },
      ],
      balancesChangedEvents: [],
      cosEvents: [],
      schemeSettlementEvents: [],
      cashEvents: [],
    },
  },
  {
    id: 'chargeback',
    title: 'Chargeback',
    subtitle: 'SD Chargeback → ACQUIRING/CHARGEBACK/LIVE/SETTLED',
    lePayload: {
      id: 'le-006',
      actionId: 'act_cb_001',
      actionRootId: 'pay_002',
      transactionVersion: 1,
      gatewayEvents: [],
      balancesChangedEvents: [],
      cosEvents: [],
      schemeSettlementEvents: [
        {
          payload: {
            settlementAmount: {
              money: { value: 200.0, currencyCode: 'EUR' },
              sign: 'DEBIT',
            },
            fees: [],
          },
          metadata: {
            scheme: 'VISA',
            transactionType: 'Chargeback',
            clientId: 'cli_acme_corp',
            settlementCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            acquirerCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
          },
        },
      ],
      cashEvents: [],
    },
  },
  {
    id: 'payout',
    title: 'Payout',
    subtitle: 'FIAPI CARD_PAYOUT → PAYOUT/CREDIT/LIVE/INITIATED',
    lePayload: {
      id: 'le-007',
      actionId: 'act_pay_001',
      actionRootId: 'act_pay_001',
      transactionVersion: 1,
      gatewayEvents: [],
      balancesChangedEvents: [
        {
          metadata: {
            clientId: 'cli_acme_corp',
            settlementCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            acquirerCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            actionType: 'CARD_PAYOUT',
            valueDate: '2026-03-22',
          },
          actions: [
            {
              changes: {
                fundingAmount: { value: 250.0, currencyCode: 'GBP' },
              },
            },
          ],
        },
      ],
      cosEvents: [],
      schemeSettlementEvents: [],
      cashEvents: [],
    },
  },
  {
    id: 'topup',
    title: 'Top-up',
    subtitle: 'FIAPI TOP_UP → TOPUP/CREDIT/LIVE/INITIATED, funds flow client→CKO',
    lePayload: {
      id: 'le-008',
      actionId: 'act_top_001',
      actionRootId: 'act_top_001',
      transactionVersion: 1,
      gatewayEvents: [],
      balancesChangedEvents: [
        {
          metadata: {
            clientId: 'cli_acme_corp',
            settlementCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            acquirerCompanyCkoLegalEntityCode: 'CKO_UK_LTD',
            actionType: 'TOP_UP',
            valueDate: '2026-03-22',
          },
          actions: [
            {
              changes: {
                fundingAmount: { value: 1000.0, currencyCode: 'EUR' },
              },
            },
          ],
        },
      ],
      cosEvents: [],
      schemeSettlementEvents: [],
      cashEvents: [],
    },
  },
  {
    id: 'cash-settlement',
    title: 'Cash Settlement',
    subtitle: 'CASH event only → CASH/SETTLEMENT/LIVE/SETTLED',
    lePayload: {
      id: 'le-009',
      actionId: 'act_cash_001',
      actionRootId: 'act_cash_001',
      transactionVersion: 1,
      gatewayEvents: [],
      balancesChangedEvents: [],
      cosEvents: [],
      schemeSettlementEvents: [],
      cashEvents: [
        {
          standardPayload: {
            amount: { value: 5000.0, currencyCode: 'EUR' },
            direction: 'credit',
          },
          standardMetadata: {
            scheme: 'SWIFT',
            actionType: 'SETTLEMENT',
            entityId: 'cli_acme_corp',
            legalEntity: 'CKO_UK_LTD',
            valueDate: '2026-03-22',
            payoutId: 'payout-batch-001',
          },
        },
      ],
    },
  },
]
```

- [ ] **Step 3: Verify TypeScript is happy**

```bash
cd ui && npx tsc --noEmit
```

Expected: no errors. If you see "Cannot find module '...' or its corresponding type declarations" for the JSON import, that will be resolved in Task 4 when we add the import.

- [ ] **Step 4: Commit**

```bash
cd ui
git add tsconfig.app.json src/data/learnExamples.ts
git commit -m "feat(learn): add LE example payloads and enable JSON imports"
```

---

## Task 2: Add nav item + routes

**Files:**
- Modify: `ui/src/components/Layout.tsx`
- Modify: `ui/src/App.tsx`

- [ ] **Step 1: Add "Learn" to nav**

In `ui/src/components/Layout.tsx`, the `NAV_LINKS` constant is:
```typescript
const NAV_LINKS = [
  { to: '/', label: 'Dashboard' },
  { to: '/dlq', label: 'DLQ' },
  { to: '/config', label: 'Config' },
  { to: '/test-bench', label: 'Test Bench' },
  { to: '/live', label: 'Live' },
] as const
```

Add `{ to: '/learn', label: 'Learn' }`:
```typescript
const NAV_LINKS = [
  { to: '/', label: 'Dashboard' },
  { to: '/dlq', label: 'DLQ' },
  { to: '/config', label: 'Config' },
  { to: '/test-bench', label: 'Test Bench' },
  { to: '/live', label: 'Live' },
  { to: '/learn', label: 'Learn' },
] as const
```

- [ ] **Step 2: Add routes in App.tsx**

Current `ui/src/App.tsx`:
```typescript
import { Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import Dashboard from './pages/Dashboard'
import DlqPage from './pages/DlqPage'
import ConfigEditor from './pages/ConfigEditor'
import TestBench from './pages/TestBench'
import LiveScreen from './pages/LiveScreen'

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/dlq" element={<DlqPage />} />
        <Route path="/config" element={<ConfigEditor />} />
        <Route path="/test-bench" element={<TestBench />} />
        <Route path="/live" element={<LiveScreen />} />
      </Route>
    </Routes>
  )
}

export default App
```

Replace with:
```typescript
import { Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/Layout'
import Dashboard from './pages/Dashboard'
import DlqPage from './pages/DlqPage'
import ConfigEditor from './pages/ConfigEditor'
import TestBench from './pages/TestBench'
import LiveScreen from './pages/LiveScreen'
import LearnLayout from './pages/learn/LearnLayout'
import SchemaExplorer from './pages/learn/SchemaExplorer'
import ExamplesPage from './pages/learn/ExamplesPage'
import LEvsNexus from './pages/learn/LEvsNexus'

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/dlq" element={<DlqPage />} />
        <Route path="/config" element={<ConfigEditor />} />
        <Route path="/test-bench" element={<TestBench />} />
        <Route path="/live" element={<LiveScreen />} />
        <Route path="/learn" element={<LearnLayout />}>
          <Route index element={<Navigate to="/learn/schema" replace />} />
          <Route path="schema" element={<SchemaExplorer />} />
          <Route path="examples" element={<ExamplesPage />} />
          <Route path="le-vs-nexus" element={<LEvsNexus />} />
        </Route>
      </Route>
    </Routes>
  )
}

export default App
```

- [ ] **Step 3: Create stub files so the app compiles**

Create `ui/src/pages/learn/LearnLayout.tsx`:
```typescript
import { NavLink, Outlet } from 'react-router-dom'

const SUB_NAV = [
  { to: '/learn/schema', label: 'Schema' },
  { to: '/learn/examples', label: 'Examples' },
  { to: '/learn/le-vs-nexus', label: 'LE vs Nexus' },
]

export default function LearnLayout() {
  return (
    <div className="-m-6">
      <div className="border-b border-zinc-200 bg-white px-6">
        <div className="flex gap-1 py-2">
          {SUB_NAV.map(({ to, label }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `px-4 py-1.5 rounded-md text-sm font-medium transition-all ${
                  isActive
                    ? 'text-zinc-900 bg-navy-600'
                    : 'text-zinc-500 hover:text-zinc-900'
                }`
              }
            >
              {label}
            </NavLink>
          ))}
        </div>
      </div>
      <Outlet />
    </div>
  )
}
```

Create `ui/src/pages/learn/SchemaExplorer.tsx` (stub):
```typescript
export default function SchemaExplorer() {
  return <div className="p-6">Schema Explorer — coming soon</div>
}
```

Create `ui/src/pages/learn/ExamplesPage.tsx` (stub):
```typescript
export default function ExamplesPage() {
  return <div className="p-6">Examples — coming soon</div>
}
```

Create `ui/src/pages/learn/LEvsNexus.tsx` (stub):
```typescript
export default function LEvsNexus() {
  return <div className="p-6">LE vs Nexus — coming soon</div>
}
```

- [ ] **Step 4: Verify the app runs and "Learn" appears in nav**

```bash
cd ui && npm run dev
```

Open http://localhost:5173. Verify "Learn" appears in the nav. Click it — should land on Schema sub-page stub. Sub-nav (Schema / Examples / LE vs Nexus) should be visible. No console errors.

- [ ] **Step 5: Commit**

```bash
cd ui
git add src/App.tsx src/components/Layout.tsx src/pages/learn/
git commit -m "feat(learn): scaffold Learn section routing and sub-nav"
```

---

## Task 3: Schema Explorer

**Files:**
- Replace stub: `ui/src/pages/learn/SchemaExplorer.tsx`

The schema is at `schema/nexus.schema.json` from the repo root. From `ui/src/pages/learn/`, the relative path is `../../../../schema/nexus.schema.json`.

The schema structure you'll work with:
```typescript
// Top-level keys relevant to the tree:
// schema.properties           → Transaction fields
// schema.$defs.Trade          → Trade fields
// schema.$defs.TradeMetadata  → TradeMetadata fields
// schema.$defs.Leg            → Leg fields
// schema.$defs.Party          → Party fields
// schema.$defs.Fee            → Fee fields
```

Each `properties` entry looks like:
```json
{
  "type": "string",
  "description": "...",
  "x-le-source": "action_id",
  "enum": ["A", "B"]   // optional
}
```

- [ ] **Step 1: Replace the SchemaExplorer stub**

```typescript
import { useState } from 'react'
import schema from '../../../../schema/nexus.schema.json'

type SchemaNode = {
  id: string
  label: string
  properties: Record<string, SchemaProperty>
  required: string[]
  description: string
}

type SchemaProperty = {
  type?: string | string[]
  description?: string
  'x-le-source'?: string
  enum?: string[]
  $ref?: string
  items?: { $ref?: string }
  format?: string
}

const defs = schema.$defs as Record<string, { properties: Record<string, SchemaProperty>; required?: string[]; description?: string }>

const TREE_NODES: SchemaNode[] = [
  {
    id: 'transaction',
    label: 'Transaction',
    properties: schema.properties as Record<string, SchemaProperty>,
    required: schema.required,
    description: schema.description,
  },
  {
    id: 'trade',
    label: 'Trade',
    properties: defs.Trade.properties,
    required: defs.Trade.required ?? [],
    description: defs.Trade.description ?? '',
  },
  {
    id: 'trademetadata',
    label: 'TradeMetadata',
    properties: defs.TradeMetadata.properties,
    required: defs.TradeMetadata.required ?? [],
    description: defs.TradeMetadata.description ?? '',
  },
  {
    id: 'leg',
    label: 'Leg',
    properties: defs.Leg.properties,
    required: defs.Leg.required ?? [],
    description: defs.Leg.description ?? '',
  },
  {
    id: 'party',
    label: 'Party',
    properties: defs.Party.properties,
    required: defs.Party.required ?? [],
    description: defs.Party.description ?? '',
  },
  {
    id: 'fee',
    label: 'Fee',
    properties: defs.Fee.properties,
    required: defs.Fee.required ?? [],
    description: defs.Fee.description ?? '',
  },
]

function typeLabel(prop: SchemaProperty): string {
  if (prop.enum) return 'enum'
  if (prop.$ref) return prop.$ref.split('/').pop() ?? 'object'
  if (prop.items?.$ref) return `${prop.items.$ref.split('/').pop()}[]`
  if (Array.isArray(prop.type)) return prop.type.join(' | ')
  return prop.type ?? 'any'
}

function TypeBadge({ prop }: { prop: SchemaProperty }) {
  const label = typeLabel(prop)
  const colour =
    label === 'string' ? 'text-blue-600 bg-blue-50'
    : label === 'number' ? 'text-emerald-600 bg-emerald-50'
    : label === 'boolean' ? 'text-purple-600 bg-purple-50'
    : label === 'enum' ? 'text-amber-600 bg-amber-50'
    : label.endsWith('[]') ? 'text-violet-600 bg-violet-50'
    : 'text-zinc-600 bg-zinc-100'
  return (
    <span className={`inline-block px-2 py-0.5 rounded text-xs font-mono font-medium ${colour}`}>
      {label}
    </span>
  )
}

export default function SchemaExplorer() {
  const [activeId, setActiveId] = useState('transaction')
  const active = TREE_NODES.find((n) => n.id === activeId) ?? TREE_NODES[0]

  return (
    <div className="flex" style={{ height: 'calc(100vh - 104px)' }}>
      {/* Sidebar */}
      <aside className="w-52 border-r border-zinc-200 bg-white overflow-y-auto flex-shrink-0">
        <div className="p-3">
          <p className="text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-2">
            Schema Nodes
          </p>
          {TREE_NODES.map((node) => (
            <button
              key={node.id}
              onClick={() => setActiveId(node.id)}
              className={`w-full text-left px-3 py-2 rounded-md text-sm mb-0.5 border-l-2 transition-all ${
                activeId === node.id
                  ? 'border-accent text-zinc-900 bg-navy-700 font-medium'
                  : 'border-transparent text-zinc-500 hover:text-zinc-900 hover:bg-navy-800'
              }`}
            >
              {node.label}
            </button>
          ))}
        </div>
      </aside>

      {/* Detail panel */}
      <main className="flex-1 overflow-y-auto p-6 bg-navy-800">
        <div className="max-w-4xl">
          <h2 className="text-lg font-semibold text-zinc-900 mb-1">{active.label}</h2>
          <p className="text-sm text-zinc-500 mb-6">{active.description}</p>

          <div className="bg-white rounded-lg border border-zinc-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-zinc-100 bg-navy-700">
                  <th className="text-left px-4 py-2.5 text-xs font-semibold text-zinc-500 uppercase tracking-wider w-48">Field</th>
                  <th className="text-left px-4 py-2.5 text-xs font-semibold text-zinc-500 uppercase tracking-wider w-28">Type</th>
                  <th className="text-left px-4 py-2.5 text-xs font-semibold text-zinc-500 uppercase tracking-wider w-20">Required</th>
                  <th className="text-left px-4 py-2.5 text-xs font-semibold text-zinc-500 uppercase tracking-wider">Description</th>
                  <th className="text-left px-4 py-2.5 text-xs font-semibold text-zinc-500 uppercase tracking-wider w-36">LE Source</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(active.properties).map(([name, prop]) => {
                  const isRequired = active.required.includes(name)
                  return (
                    <tr key={name} className="border-b border-zinc-50 hover:bg-navy-800 transition-colors">
                      <td className="px-4 py-3">
                        <span className="font-mono text-xs text-zinc-800">{name}</span>
                      </td>
                      <td className="px-4 py-3">
                        <TypeBadge prop={prop} />
                      </td>
                      <td className="px-4 py-3">
                        {isRequired ? (
                          <span className="text-xs font-medium text-rose-600">required</span>
                        ) : (
                          <span className="text-xs text-zinc-400">optional</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-xs text-zinc-600 leading-relaxed">
                        {prop.description ?? '—'}
                        {prop.enum && (
                          <div className="mt-1 flex flex-wrap gap-1">
                            {prop.enum.map((v) => (
                              <span key={v} className="inline-block px-1.5 py-0.5 bg-amber-50 text-amber-700 rounded text-xs font-mono">
                                {v}
                              </span>
                            ))}
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        {prop['x-le-source'] ? (
                          <span className="font-mono text-xs text-zinc-500 bg-zinc-50 px-1.5 py-0.5 rounded">
                            {prop['x-le-source']}
                          </span>
                        ) : (
                          <span className="text-xs text-zinc-300">—</span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      </main>
    </div>
  )
}
```

- [ ] **Step 2: Verify in browser**

Run `npm run dev`. Navigate to http://localhost:5173/learn/schema.

Check:
- Six nodes appear in the sidebar (Transaction, Trade, TradeMetadata, Leg, Party, Fee)
- Clicking each shows the correct field table
- `transaction_id` row shows type `string`, required, has a description, has LE source `action_id`
- `status` row shows type `enum` with the three values (NOT_LIVE, LIVE, DEAD) listed
- No TypeScript errors in the terminal

- [ ] **Step 3: Commit**

```bash
cd ui
git add src/pages/learn/SchemaExplorer.tsx
git commit -m "feat(learn): implement Schema Explorer"
```

---

## Task 4: Examples page

**Files:**
- Replace stub: `ui/src/pages/learn/ExamplesPage.tsx`

This page imports `LEARN_EXAMPLES` from Task 1, displays a sidebar list, and shows a side-by-side split on selection: left = LE input JSON, right = `TransactionTrace` result of `runTestBench`.

- [ ] **Step 1: Replace the ExamplesPage stub**

```typescript
import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { runTestBench, type TestBenchResult } from '../../api/client'
import TransactionTrace from '../../components/TransactionTrace'
import { LEARN_EXAMPLES } from '../../data/learnExamples'

function JsonPanel({ payload }: { payload: unknown }) {
  const [collapsed, setCollapsed] = useState(false)
  const json = JSON.stringify(payload, null, 2)

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between px-4 py-2 border-b border-zinc-200 bg-navy-700 flex-shrink-0">
        <span className="text-xs font-semibold text-zinc-500 uppercase tracking-wider">LE Event In</span>
        <button
          onClick={() => setCollapsed((c) => !c)}
          className="text-xs text-zinc-400 hover:text-zinc-700 transition-colors"
        >
          {collapsed ? 'expand' : 'collapse'}
        </button>
      </div>
      {!collapsed && (
        <pre className="flex-1 overflow-auto p-4 text-xs font-mono text-zinc-700 bg-white leading-relaxed">
          {json}
        </pre>
      )}
    </div>
  )
}

function NexusPanel({ result, isPending, error }: { result: TestBenchResult | undefined; isPending: boolean; error: Error | null }) {
  return (
    <div className="flex flex-col h-full border-l border-zinc-200">
      <div className="px-4 py-2 border-b border-zinc-200 bg-navy-700 flex-shrink-0">
        <span className="text-xs font-semibold text-zinc-500 uppercase tracking-wider">Nexus Block Out</span>
      </div>
      <div className="flex-1 overflow-auto p-4 bg-navy-800">
        {isPending && (
          <div className="flex items-center gap-2 text-sm text-zinc-400">
            <span className="inline-block w-3 h-3 rounded-full bg-accent animate-pulse" />
            Running transformer…
          </div>
        )}
        {error && (
          <div className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg p-3">
            {error.message}
          </div>
        )}
        {result && !isPending && (
          result.success && result.transaction ? (
            <TransactionTrace data={result.transaction} />
          ) : (
            <div className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg p-3">
              <p className="font-medium mb-1">Transformation failed</p>
              {result.errors?.map((e, i) => <p key={i} className="text-xs">{e}</p>)}
            </div>
          )
        )}
        {!result && !isPending && !error && (
          <p className="text-sm text-zinc-400 italic">Select a scenario to run it</p>
        )}
      </div>
    </div>
  )
}

export default function ExamplesPage() {
  const [activeId, setActiveId] = useState<string | null>(null)
  const [activePayload, setActivePayload] = useState<unknown>(null)

  const mutation = useMutation<TestBenchResult, Error, unknown>({
    mutationFn: runTestBench,
  })

  function selectExample(id: string) {
    const example = LEARN_EXAMPLES.find((e) => e.id === id)
    if (!example) return
    setActiveId(id)
    setActivePayload(example.lePayload)
    mutation.mutate(example.lePayload)
  }

  return (
    <div className="flex" style={{ height: 'calc(100vh - 104px)' }}>
      {/* Sidebar */}
      <aside className="w-56 border-r border-zinc-200 bg-white overflow-y-auto flex-shrink-0">
        <div className="p-3">
          <p className="text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-2">
            Scenarios
          </p>
          {LEARN_EXAMPLES.map((ex) => (
            <button
              key={ex.id}
              onClick={() => selectExample(ex.id)}
              className={`w-full text-left px-3 py-2.5 rounded-md mb-0.5 border-l-2 transition-all ${
                activeId === ex.id
                  ? 'border-accent bg-navy-700 text-zinc-900'
                  : 'border-transparent hover:bg-navy-800 text-zinc-500 hover:text-zinc-900'
              }`}
            >
              <p className="text-sm font-medium">{ex.title}</p>
              <p className="text-xs text-zinc-400 mt-0.5 leading-tight">{ex.subtitle}</p>
            </button>
          ))}
        </div>
      </aside>

      {/* Split panel */}
      <div className="flex-1 grid grid-cols-2 min-h-0 overflow-hidden">
        <div className="flex flex-col min-h-0 overflow-hidden border-r border-zinc-200">
          {activePayload ? (
            <JsonPanel payload={activePayload} />
          ) : (
            <div className="flex items-center justify-center h-full text-sm text-zinc-400 italic">
              Select a scenario
            </div>
          )}
        </div>
        <NexusPanel
          result={mutation.data}
          isPending={mutation.isPending}
          error={mutation.error}
        />
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Verify in browser (requires backend running)**

Start the full stack (`docker compose up` + services) or use an already-running backend.

Navigate to http://localhost:5173/learn/examples. Click "Simple Capture":
- Left panel shows the LE event JSON, with a collapse/expand toggle
- Right panel shows "Running transformer…" briefly, then shows the Nexus block via `TransactionTrace`
- The Nexus block should show `NOT_LIVE`, one ACQUIRING/CAPTURE trade, with a FUNDING leg

Click "Settled Capture":
- Right panel should show `LIVE`, ACQUIRING/CAPTURE/SETTLED, with SCHEME_SETTLEMENT and FUNDING legs

If the backend is not running, the right panel shows the API error message — that's expected.

- [ ] **Step 3: Commit**

```bash
cd ui
git add src/pages/learn/ExamplesPage.tsx
git commit -m "feat(learn): implement Examples page with live transformer"
```

---

## Task 5: LE vs Nexus page

**Files:**
- Replace stub: `ui/src/pages/learn/LEvsNexus.tsx`

This page has two sections: narrative callout cards (static, no data needed) and a field mapping table (hardcoded from `le_nexus_mapping.md`).

- [ ] **Step 1: Replace the LEvsNexus stub**

```typescript
interface Principle {
  title: string
  body: string
}

interface MappingRow {
  leField: string
  nexusField: string | null
  notes: string
}

interface PillarGroup {
  pillar: string
  rows: MappingRow[]
}

const PRINCIPLES: Principle[] = [
  {
    title: 'LE is cumulative — Nexus reads latest',
    body: 'LE emits a new full record every time a pillar event arrives for an action_id. Nexus always consumes the record with the highest transaction_version — it never processes deltas.',
  },
  {
    title: 'One action_id = one Nexus block',
    body: 'Nexus is not a ledger and does not track balances. It models financial events. Each LE action_id produces exactly one Nexus block.',
  },
  {
    title: 'Partial state is normal',
    body: 'Pillars arrive incrementally: GW → COS → FIAPI → SD → Cash. Legs are added to the block as each pillar arrives. A block without SD data is expected — not an error.',
  },
  {
    title: 'PREDICTED vs ACTUAL legs',
    body: 'Legs sourced from COS carry status PREDICTED — they reflect expected settlement. Legs sourced from SD carry status ACTUAL — they reflect confirmed settlement.',
  },
  {
    title: 'LE internals are not surfaced',
    body: 'Fields like transaction_version, transaction_state_id, id, key, and metadata are LE operational concepts used for Kafka routing and idempotency. Nexus does not expose them.',
  },
  {
    title: 'status is computed, not mapped',
    body: 'NOT_LIVE / LIVE / DEAD is determined by Nexus based on data completeness thresholds. It has no equivalent field in LE — it is produced entirely by the Nexus transformer.',
  },
]

const MAPPING_GROUPS: PillarGroup[] = [
  {
    pillar: 'Top-level',
    rows: [
      { leField: 'action_id', nexusField: 'nexus_id, action_id', notes: 'Used as nexus_id and preserved for traceability' },
      { leField: 'action_root_id', nexusField: 'parent_nexus_id, action_root_id', notes: 'Groups payment lifecycle; preserved for traceability' },
      { leField: 'transaction_version', nexusField: null, notes: 'Used internally to select latest version; not in contract' },
      { leField: 'transaction_state_id', nexusField: null, notes: 'Stable LE ID across versions — internal concept' },
      { leField: 'id', nexusField: null, notes: 'Unique BQ record ID — internal' },
      { leField: 'key', nexusField: null, notes: 'Kafka partition key — operational only' },
      { leField: 'metadata', nexusField: null, notes: 'Kafka metadata (ingestion_timestamp, partition, offset) — operational only' },
      { leField: '(not in LE)', nexusField: 'status', notes: 'Computed by Nexus from data completeness (NOT_LIVE/LIVE/DEAD)' },
    ],
  },
  {
    pillar: 'Gateway',
    rows: [
      { leField: 'gatewayEvents[0].eventType', nexusField: 'transactions[].transaction_type (via mapping)', notes: 'payment_captured → CAPTURE, payment_refunded → REFUND, etc.' },
      { leField: 'gatewayEvents[0].processedOn', nexusField: 'processed_at', notes: 'Primary source for processed_at' },
      { leField: 'gatewayEvents[0].amount.value', nexusField: 'transactions[].transaction_amount', notes: 'Trade amount in gateway currency' },
      { leField: 'gatewayEvents[0].amount.currencyCode', nexusField: 'transactions[].transaction_currency', notes: 'Trade currency' },
      { leField: 'gatewayEvents[0].acquirerName', nexusField: 'transactions[].metadata.acquirer_name', notes: 'Fallback if FIAPI/SD absent' },
      { leField: 'gatewayEvents[0].acquirerCountry', nexusField: 'transactions[].metadata.acquirer_country', notes: 'Fallback if FIAPI/SD absent' },
    ],
  },
  {
    pillar: 'COS',
    rows: [
      { leField: 'cosEvents[0].payload.fee', nexusField: 'transactions[].legs[].fees[]', notes: 'Predicted fee amount' },
      { leField: 'cosEvents[0].payload.feeType', nexusField: 'transactions[].legs[].fees[].fee_type', notes: 'FEE_TYPE_INTERCHANGE → INTERCHANGE, FEE_TYPE_SCHEME → SCHEME_FEE' },
      { leField: 'cosEvents[0].payload.isPredicted', nexusField: 'transactions[].legs[].leg_status = PREDICTED', notes: 'COS always produces PREDICTED legs' },
      { leField: 'cosEvents[0].metadata.reconciliationReference', nexusField: 'transactions[].metadata.reconciliation_reference', notes: '' },
    ],
  },
  {
    pillar: 'FIAPI (balancesChangedEvents)',
    rows: [
      { leField: 'balancesChangedEvents[0].metadata.actionType', nexusField: 'transactions[].transaction_type + family', notes: 'CARD_PAYOUT → PAYOUT family; TOP_UP → TOPUP family' },
      { leField: 'balancesChangedEvents[0].metadata.clientId', nexusField: 'entity.id', notes: 'Primary source for entity identifier' },
      { leField: 'balancesChangedEvents[0].metadata.settlementCompanyCkoLegalEntityCode', nexusField: 'cko_entity_id', notes: 'CKO legal entity' },
      { leField: 'balancesChangedEvents[0].metadata.valueDate', nexusField: 'transactions[].legs[].value_date', notes: 'Funding leg value date (arrears logic)' },
      { leField: 'balancesChangedEvents[0].metadata.clientSettlementType', nexusField: 'transactions[].metadata.client_settlement_type', notes: 'Gross/Net settlement type' },
    ],
  },
  {
    pillar: 'Scheme Settlement (SD)',
    rows: [
      { leField: 'schemeSettlementEvents[0].metadata.scheme', nexusField: 'transactions[].metadata.scheme_code', notes: 'Primary source for scheme (VISA, MASTERCARD, etc.)' },
      { leField: 'schemeSettlementEvents[0].metadata.transactionType', nexusField: 'transactions[].transaction_type (via mapping)', notes: 'Capture, Refund, Chargeback → CAPTURE, REFUND, CHARGEBACK' },
      { leField: 'schemeSettlementEvents[0].payload.settlementAmount', nexusField: 'transactions[].legs[SCHEME_SETTLEMENT].leg_amount', notes: 'Scheme → CKO settlement amount. Currency may differ from GW (cross-currency)' },
      { leField: 'schemeSettlementEvents[0].payload.fees[]', nexusField: 'transactions[].legs[].fees[]', notes: 'SD-sourced fees are ACTUAL' },
      { leField: 'schemeSettlementEvents[0].metadata.expectedValueDate', nexusField: 'transactions[].legs[].value_date', notes: 'Value date for SCHEME_SETTLEMENT leg' },
      { leField: 'schemeSettlementEvents[0].metadata.settlementServiceName', nexusField: 'transactions[].metadata.settlement_service_name', notes: '' },
      { leField: 'schemeSettlementEvents[0].metadata.clientId', nexusField: 'entity.id', notes: 'Fallback if FIAPI absent' },
    ],
  },
  {
    pillar: 'Cash',
    rows: [
      { leField: 'cashEvents[0].standardPayload.amount', nexusField: 'transactions[].transaction_amount', notes: 'Cash movement amount' },
      { leField: 'cashEvents[0].standardPayload.direction', nexusField: 'leg from/to_party direction', notes: 'credit = CKO→CLIENT, debit = CLIENT→CKO' },
      { leField: 'cashEvents[0].standardMetadata.actionType', nexusField: 'transactions[].transaction_type', notes: 'SETTLEMENT, RECEIPT, PAYMENT' },
      { leField: 'cashEvents[0].standardMetadata.entityId', nexusField: 'entity.id', notes: 'Client identifier from cash event' },
      { leField: 'cashEvents[0].standardMetadata.payoutId', nexusField: 'transactions[].metadata.payout_id', notes: '' },
      { leField: 'cashEvents[0].standardMetadata.valueDate', nexusField: 'transactions[].legs[].value_date', notes: '' },
    ],
  },
]

export default function LEvsNexus() {
  return (
    <div className="overflow-y-auto bg-navy-800" style={{ height: 'calc(100vh - 104px)' }}>
      <div className="max-w-4xl mx-auto p-6 space-y-10">

        {/* Narrative */}
        <section>
          <h2 className="text-lg font-semibold text-zinc-900 mb-1">Key Differences</h2>
          <p className="text-sm text-zinc-500 mb-4">
            Nexus is not a replica of the Linking Engine — it is a clean-slate contract designed around financial events, not LE internals.
          </p>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {PRINCIPLES.map((p) => (
              <div key={p.title} className="bg-white rounded-lg border border-zinc-200 p-4">
                <h3 className="text-sm font-semibold text-zinc-800 mb-1">{p.title}</h3>
                <p className="text-xs text-zinc-500 leading-relaxed">{p.body}</p>
              </div>
            ))}
          </div>
        </section>

        {/* Field mapping */}
        <section>
          <h2 className="text-lg font-semibold text-zinc-900 mb-1">Field Mapping</h2>
          <p className="text-sm text-zinc-500 mb-4">
            How LE fields map to Nexus fields, grouped by pillar. Fields with no Nexus equivalent are LE internals not surfaced in the contract.
          </p>
          <div className="space-y-6">
            {MAPPING_GROUPS.map((group) => (
              <div key={group.pillar}>
                <h3 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-2">
                  {group.pillar}
                </h3>
                <div className="bg-white rounded-lg border border-zinc-200 overflow-hidden">
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="border-b border-zinc-100 bg-navy-700">
                        <th className="text-left px-4 py-2 text-zinc-500 font-semibold uppercase tracking-wider w-2/5">LE Field</th>
                        <th className="text-left px-4 py-2 text-zinc-500 font-semibold uppercase tracking-wider w-2/5">Nexus Field</th>
                        <th className="text-left px-4 py-2 text-zinc-500 font-semibold uppercase tracking-wider">Notes</th>
                      </tr>
                    </thead>
                    <tbody>
                      {group.rows.map((row, i) => (
                        <tr key={i} className="border-b border-zinc-50 hover:bg-navy-800 transition-colors">
                          <td className="px-4 py-2.5 font-mono text-zinc-700">{row.leField}</td>
                          <td className="px-4 py-2.5">
                            {row.nexusField ? (
                              <span className="font-mono text-accent">{row.nexusField}</span>
                            ) : (
                              <span className="text-zinc-400 italic">(not surfaced)</span>
                            )}
                          </td>
                          <td className="px-4 py-2.5 text-zinc-500">{row.notes}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            ))}
          </div>
        </section>

      </div>
    </div>
  )
}
```

- [ ] **Step 2: Verify in browser**

Navigate to http://localhost:5173/learn/le-vs-nexus.

Check:
- Six principle cards visible in a 2-column grid
- Six pillar sections in the mapping table (Top-level, Gateway, COS, FIAPI, SD, Cash)
- Rows with no Nexus equivalent show "(not surfaced)" in muted style
- Page scrolls correctly within the content area

- [ ] **Step 3: Commit**

```bash
cd ui
git add src/pages/learn/LEvsNexus.tsx
git commit -m "feat(learn): implement LE vs Nexus comparison page"
```

---

## Task 6: Final verification

- [ ] **Step 1: Build check**

```bash
cd ui && npm run build
```

Expected: clean build, no TypeScript errors.

- [ ] **Step 2: Smoke-test all three sub-pages**

With the full stack running, verify:

| Page | Check |
|------|-------|
| `/learn/schema` | All 6 tree nodes work; Transaction node shows `transaction_id` with LE source `action_id` |
| `/learn/examples` | Clicking "Simple Capture" shows a Nexus block with `NOT_LIVE` status |
| `/learn/examples` | Clicking "Cash Settlement" shows a CASH family block |
| `/learn/le-vs-nexus` | Narrative cards and all 6 pillar mapping groups render |
| Nav | "Learn" in top nav, active state highlights correctly |
| Sub-nav | Schema / Examples / LE vs Nexus tabs, active highlight follows route |

- [ ] **Step 3: Final commit**

```bash
cd ui
git commit --allow-empty -m "feat(learn): Learn section complete — schema explorer, examples, LE vs Nexus"
```
