import { useState } from 'react'
import schemaRaw from '../../../../schema/nexus.schema.json'
import { ProductMatrixGrid } from './ProductMatrix'

interface PropertyDef {
  type?: string
  $ref?: string
  enum?: string[]
  description?: string
  'x-le-source'?: string
  items?: { $ref?: string }
}

interface NodeSchema {
  type?: string
  description?: string
  required?: string[]
  properties?: Record<string, PropertyDef>
}

interface SchemaRoot extends NodeSchema {
  $defs?: Record<string, NodeSchema>
}

const schema = schemaRaw as unknown as SchemaRoot

type NodeName = 'Overview' | 'Block' | 'Transaction' | 'Transaction Metadata' | 'Leg' | 'Party' | 'Fee'

const NODE_NAMES: NodeName[] = ['Overview', 'Block', 'Transaction', 'Transaction Metadata', 'Leg', 'Party', 'Fee']

function getNodeSchema(node: NodeName): NodeSchema {
  const defs = schema.$defs ?? {}
  switch (node) {
    case 'Overview':
      return {}
    case 'Block':
      return schema
    case 'Transaction':
      return defs['Transaction'] ?? {}
    case 'Transaction Metadata':
      return defs['TransactionMetadata'] ?? {}
    case 'Leg':
      return defs['Leg'] ?? {}
    case 'Party':
      return defs['Party'] ?? {}
    case 'Fee':
      return defs['Fee'] ?? {}
  }
}

function resolveType(prop: PropertyDef): string {
  if (prop.$ref) { const s = prop.$ref.split('/'); return s[s.length - 1] }
  if (prop.type === 'array' && prop.items?.$ref) { const s = prop.items.$ref.split('/'); return s[s.length - 1] + '[]' }
  if (prop.enum) return 'enum'
  return prop.type ?? '?'
}

interface FieldRow {
  name: string
  type: string
  required: boolean
  description: string
  leSource: string | null
}

function buildRows(nodeSchema: NodeSchema): { rows: FieldRow[]; hasLeSource: boolean } {
  const props = nodeSchema.properties ?? {}
  const requiredSet = new Set(nodeSchema.required ?? [])
  let hasLeSource = false

  const rows: FieldRow[] = Object.entries(props).map(([name, prop]) => {
    const leSource = prop['x-le-source'] ?? null
    if (leSource !== null) hasLeSource = true
    return {
      name,
      type: resolveType(prop),
      required: requiredSet.has(name),
      description: prop.description ?? '',
      leSource,
    }
  })

  return { rows, hasLeSource }
}

function NestedBox({
  label,
  fields,
  borderClass,
  labelClass,
  children,
}: {
  label: string
  fields: string
  borderClass: string
  labelClass: string
  children?: React.ReactNode
}) {
  return (
    <div className={`border-2 ${borderClass} rounded-xl p-4 relative`}>
      <span className={`absolute -top-3 left-3 px-2 text-xs font-bold ${labelClass} bg-white`}>
        {label}
      </span>
      <p className="text-[10px] text-zinc-400 mb-3 font-mono">{fields}</p>
      {children}
    </div>
  )
}

const CALLOUT_CARDS = [
  {
    label: 'Block',
    borderClass: 'border-l-2 border-cyan-400',
    bgClass: 'bg-cyan-50',
    labelClass: 'text-cyan-700',
    description: 'One per action_id. The envelope for the whole payment lifecycle.',
  },
  {
    label: 'Transaction',
    borderClass: 'border-l-2 border-indigo-400',
    bgClass: 'bg-indigo-50',
    labelClass: 'text-indigo-700',
    description: 'One per product type (ACQUIRING, PAYOUT…). Classifies the trade family.',
  },
  {
    label: 'Leg',
    borderClass: 'border-l-2 border-emerald-400',
    bgClass: 'bg-emerald-50',
    labelClass: 'text-emerald-700',
    description: 'A money movement: from_party → to_party with amount, currency, and status.',
  },
  {
    label: 'Party',
    borderClass: 'border-l-2 border-amber-400',
    bgClass: 'bg-amber-50',
    labelClass: 'text-amber-700',
    description: 'An account endpoint: entity, bank account, or scheme participant.',
  },
  {
    label: 'Fee',
    borderClass: 'border-l-2 border-rose-400',
    bgClass: 'bg-rose-50',
    labelClass: 'text-rose-700',
    description: 'A cost attached to a leg (interchange, scheme fee, processing fee…).',
  },
]

function OverviewPanel() {
  return (
    <div className="space-y-10">
    <div className="flex gap-8 items-start">
      {/* Left: nested boxes diagram — 2/3 width */}
      <div className="flex-[2] min-w-0 pt-4">
        <NestedBox
          label="BLOCK"
          fields="nexus_id · action_id · status · entity · cko_entity_id"
          borderClass="border-cyan-300"
          labelClass="text-cyan-700"
        >
          <NestedBox
            label="TRANSACTION [ ]"
            fields="product_type · transaction_type · transaction_status · transaction_id"
            borderClass="border-indigo-300"
            labelClass="text-indigo-700"
          >
            <div className="flex gap-3">
              {/* Leg: 2/3 */}
              <div className="flex-[2] min-w-0">
                <NestedBox
                  label="LEG [ ]"
                  fields="leg_type · leg_amount · leg_currency · leg_status"
                  borderClass="border-emerald-300"
                  labelClass="text-emerald-700"
                >
                  <div className="flex gap-2 mt-1">
                    <div className="flex-1 border-2 border-amber-300 rounded-lg px-3 py-2 text-center">
                      <p className="text-[10px] font-bold text-amber-700">PARTY ×2</p>
                      <p className="text-[9px] text-zinc-400 font-mono">from · to</p>
                    </div>
                    <div className="flex-1 border-2 border-rose-300 rounded-lg px-3 py-2 text-center">
                      <p className="text-[10px] font-bold text-rose-700">FEE [ ]</p>
                      <p className="text-[9px] text-zinc-400 font-mono">type · amount</p>
                    </div>
                  </div>
                </NestedBox>
              </div>
              {/* Metadata: 1/3 */}
              <div className="flex-[1] border border-zinc-200 rounded-lg px-3 py-2 bg-white">
                <p className="text-[9px] font-semibold text-zinc-400 uppercase tracking-wider mb-1">Metadata</p>
                <p className="text-[9px] text-zinc-300 font-mono leading-relaxed">
                  scheme_code<br />
                  acquirer_name<br />
                  payment_method<br />
                  …
                </p>
              </div>
            </div>
          </NestedBox>
        </NestedBox>
      </div>

      {/* Right: callout cards — 1/3 width */}
      <div className="flex-[1] flex flex-col gap-2">
        {CALLOUT_CARDS.map((card) => (
          <div
            key={card.label}
            className={`${card.borderClass} ${card.bgClass} rounded-r-lg px-3 py-2`}
          >
            <p className={`text-sm font-semibold ${card.labelClass}`}>{card.label}</p>
            <p className="text-xs text-zinc-600 mt-0.5">{card.description}</p>
          </div>
        ))}
      </div>
    </div>
    <ProductMatrixGrid />
    </div>
  )
}

export default function SchemaExplorer() {
  const [active, setActive] = useState<NodeName>('Overview')

  const nodeSchema = active === 'Overview' ? {} : getNodeSchema(active)
  const { rows, hasLeSource } = buildRows(nodeSchema)

  return (
    <div className="flex gap-4 h-full">
      <nav className="w-48 shrink-0 space-y-0.5">
        {NODE_NAMES.map((node) => (
          <button
            key={node}
            onClick={() => setActive(node)}
            className={`w-full text-left px-3 py-2 text-sm rounded transition-colors ${
              active === node
                ? 'border-l-2 border-cyan-500 bg-cyan-50 text-cyan-700 font-medium'
                : 'text-zinc-600 hover:bg-zinc-50'
            }`}
          >
            {node}
          </button>
        ))}
      </nav>

      <div className="flex-1 overflow-auto">
        {active === 'Overview' ? (
          <OverviewPanel />
        ) : (
          <table className="w-full border-collapse">
            <thead>
              <tr className="bg-zinc-50 text-zinc-500 uppercase tracking-wider text-[10px]">
                <th className="text-left px-3 py-2 font-semibold">Field</th>
                <th className="text-left px-3 py-2 font-semibold">Type</th>
                <th className="text-left px-3 py-2 font-semibold">Required</th>
                <th className="text-left px-3 py-2 font-semibold">Description</th>
                {hasLeSource && (
                  <th className="text-left px-3 py-2 font-semibold">LE Source</th>
                )}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.name} className="border-b border-zinc-100 bg-white hover:bg-zinc-50/50">
                  <td className="px-3 py-2 font-mono text-xs text-zinc-800">{row.name}</td>
                  <td className="px-3 py-2 font-mono text-[10px] text-blue-600">{row.type}</td>
                  <td className="px-3 py-2 text-xs">
                    {row.required ? (
                      <span className="inline-flex items-center px-1 py-0.5 rounded text-[10px] font-semibold bg-green-100 text-green-700">
                        true
                      </span>
                    ) : (
                      <span className="text-zinc-400">—</span>
                    )}
                  </td>
                  <td className="px-3 py-2 text-xs text-zinc-600 max-w-xs">
                    {row.description || <span className="text-zinc-400">—</span>}
                  </td>
                  {hasLeSource && (
                    <td className="px-3 py-2 text-xs font-mono text-zinc-500">
                      {row.leSource ?? <span className="text-zinc-300">—</span>}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
