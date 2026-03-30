import { useState } from 'react'
import schemaRaw from '../../../../schema/nexus.schema.json'

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

type NodeName = 'Transaction' | 'Trade' | 'TradeMetadata' | 'Leg' | 'Party' | 'Fee'

const NODE_NAMES: NodeName[] = ['Transaction', 'Trade', 'TradeMetadata', 'Leg', 'Party', 'Fee']

function getNodeSchema(node: NodeName): NodeSchema {
  const defs = schema.$defs ?? {}
  switch (node) {
    case 'Transaction':
      return schema
    case 'Trade':
      return defs['Transaction'] ?? {}
    case 'TradeMetadata':
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

export default function SchemaExplorer() {
  const [active, setActive] = useState<NodeName>('Transaction')
  const nodeSchema = getNodeSchema(active)
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
      </div>
    </div>
  )
}
