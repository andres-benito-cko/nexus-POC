import { useState } from 'react'
import type { NexusEngineConfigContent } from '../../pages/ConfigEditor'

interface FeeTypeMappingsTabProps {
  content: NexusEngineConfigContent | null
  onUpdate: (updater: (c: NexusEngineConfigContent) => NexusEngineConfigContent) => void
}

const PILLAR_LABELS: Record<string, string> = {
  sd: 'SD (Scheme Settlement)',
  cos: 'COS (Cost of Sales)',
  fiapi: 'FIAPI (Balances/Revenue)',
}

export default function FeeTypeMappingsTab({ content, onUpdate }: FeeTypeMappingsTabProps) {
  const [editingCell, setEditingCell] = useState<{ pillar: string; sourceType: string } | null>(null)
  const [editValue, setEditValue] = useState('')

  if (!content) {
    return (
      <div className="flex items-center justify-center h-64 text-sm text-red-500">
        Unable to parse YAML content. Fix syntax errors in the Raw YAML tab.
      </div>
    )
  }

  const feeTypeMappings = content.fee_type_mappings ?? {}
  const pillars = Object.keys(feeTypeMappings)

  function startEdit(pillar: string, sourceType: string) {
    setEditingCell({ pillar, sourceType })
    setEditValue(feeTypeMappings[pillar]?.[sourceType] ?? '')
  }

  function saveEdit() {
    if (!editingCell) return
    const { pillar, sourceType } = editingCell
    onUpdate((c) => ({
      ...c,
      fee_type_mappings: {
        ...c.fee_type_mappings,
        [pillar]: {
          ...c.fee_type_mappings?.[pillar],
          [sourceType]: editValue,
        },
      },
    }))
    setEditingCell(null)
  }

  function cancelEdit() {
    setEditingCell(null)
  }

  function addMapping(pillar: string) {
    const sourceType = prompt(`Source fee type for ${pillar.toUpperCase()}:`)
    if (!sourceType?.trim()) return
    const nexusType = prompt('Nexus fee type:')
    if (!nexusType?.trim()) return
    onUpdate((c) => ({
      ...c,
      fee_type_mappings: {
        ...c.fee_type_mappings,
        [pillar]: {
          ...c.fee_type_mappings?.[pillar],
          [sourceType.trim()]: nexusType.trim(),
        },
      },
    }))
  }

  function removeMapping(pillar: string, sourceType: string) {
    onUpdate((c) => {
      const pillarMappings = { ...c.fee_type_mappings?.[pillar] }
      delete pillarMappings[sourceType]
      return {
        ...c,
        fee_type_mappings: {
          ...c.fee_type_mappings,
          [pillar]: pillarMappings,
        },
      }
    })
  }

  function addPillar() {
    const pillar = prompt('Pillar key (e.g., sd, cos, fiapi):')
    if (!pillar?.trim()) return
    onUpdate((c) => ({
      ...c,
      fee_type_mappings: {
        ...c.fee_type_mappings,
        [pillar.trim().toLowerCase()]: {},
      },
    }))
  }

  return (
    <div className="p-5 max-w-4xl space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-sm font-semibold text-zinc-900">Fee Type Mappings</h2>
          <p className="text-xs text-zinc-400 mt-0.5">
            Maps source pillar fee types to normalized Nexus fee types.
          </p>
        </div>
        <button
          onClick={addPillar}
          className="px-3 py-1.5 rounded-md bg-accent text-white text-xs font-medium hover:bg-accent-dark transition-colors"
        >
          + Add Pillar
        </button>
      </div>

      {pillars.length === 0 && (
        <div className="flex items-center justify-center py-12 text-sm text-zinc-400">
          No fee type mappings defined. Click "Add Pillar" to start.
        </div>
      )}

      {pillars.map((pillar) => {
        const mappings = feeTypeMappings[pillar] ?? {}
        const entries = Object.entries(mappings)

        return (
          <div key={pillar} className="glow-border rounded-xl overflow-hidden">
            <div className="flex items-center justify-between px-4 py-3 bg-navy-700 border-b border-zinc-100">
              <h3 className="text-xs font-semibold text-zinc-600 uppercase tracking-wider">
                {PILLAR_LABELS[pillar] ?? pillar.toUpperCase()}
              </h3>
              <button
                onClick={() => addMapping(pillar)}
                className="px-2 py-0.5 rounded text-[10px] font-medium bg-blue-50 text-blue-600 hover:bg-blue-100"
              >
                + Add
              </button>
            </div>
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-zinc-100">
                  <th className="py-2 px-4 text-left text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                    Source Fee Type
                  </th>
                  <th className="py-2 px-4 text-left text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                    Nexus Fee Type
                  </th>
                  <th className="py-2 px-4 text-right text-xs font-semibold text-zinc-400 uppercase tracking-wider w-28">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                {entries.map(([sourceType, nexusType]) => {
                  const isEditing =
                    editingCell?.pillar === pillar && editingCell?.sourceType === sourceType

                  return (
                    <tr
                      key={sourceType}
                      className="border-b border-zinc-50 hover:bg-accent-glow transition-colors"
                    >
                      <td className="py-2 px-4 font-mono text-xs text-zinc-700">{sourceType}</td>
                      <td className="py-2 px-4">
                        {isEditing ? (
                          <input
                            type="text"
                            value={editValue}
                            onChange={(e) => setEditValue(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') saveEdit()
                              if (e.key === 'Escape') cancelEdit()
                            }}
                            autoFocus
                            className="px-2 py-0.5 rounded border border-zinc-200 text-xs font-mono bg-white focus:outline-none focus:ring-2 focus:ring-blue-300 w-48"
                          />
                        ) : (
                          <span
                            className="inline-flex items-center rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700 cursor-pointer hover:bg-emerald-100"
                            onDoubleClick={() => startEdit(pillar, sourceType)}
                          >
                            {nexusType}
                          </span>
                        )}
                      </td>
                      <td className="py-2 px-4 text-right">
                        {isEditing ? (
                          <div className="flex items-center justify-end gap-1">
                            <button
                              onClick={saveEdit}
                              className="px-2 py-0.5 rounded text-[10px] font-medium bg-emerald-50 text-emerald-700 hover:bg-emerald-100"
                            >
                              Save
                            </button>
                            <button
                              onClick={cancelEdit}
                              className="px-2 py-0.5 rounded text-[10px] font-medium bg-zinc-100 text-zinc-600 hover:bg-zinc-200"
                            >
                              Cancel
                            </button>
                          </div>
                        ) : (
                          <div className="flex items-center justify-end gap-1">
                            <button
                              onClick={() => startEdit(pillar, sourceType)}
                              className="px-2 py-0.5 rounded text-[10px] font-medium bg-zinc-100 text-zinc-600 hover:bg-zinc-200"
                            >
                              Edit
                            </button>
                            <button
                              onClick={() => removeMapping(pillar, sourceType)}
                              className="px-2 py-0.5 rounded text-[10px] font-medium bg-red-50 text-red-600 hover:bg-red-100"
                            >
                              Del
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  )
                })}
                {entries.length === 0 && (
                  <tr>
                    <td colSpan={3} className="py-4 text-center text-xs text-zinc-400">
                      No mappings for this pillar
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )
      })}
    </div>
  )
}
