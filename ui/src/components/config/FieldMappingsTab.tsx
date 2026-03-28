import { useState } from 'react'
import type { NexusEngineConfigContent } from '../../pages/ConfigEditor'

interface FieldMappingsTabProps {
  content: NexusEngineConfigContent | null
  onUpdate: (updater: (c: NexusEngineConfigContent) => NexusEngineConfigContent) => void
}

export default function FieldMappingsTab({ content, onUpdate }: FieldMappingsTabProps) {
  const [editingKey, setEditingKey] = useState<string | null>(null)
  const [editPaths, setEditPaths] = useState<string[]>([])
  const [editFallback, setEditFallback] = useState('')

  if (!content) {
    return (
      <div className="flex items-center justify-center h-64 text-sm text-red-500">
        Unable to parse YAML content. Fix syntax errors in the Raw YAML tab.
      </div>
    )
  }

  const mappings = content.field_mappings ?? {}
  const keys = Object.keys(mappings)

  function startEdit(key: string) {
    const mapping = mappings[key]
    setEditingKey(key)
    setEditPaths([...(mapping?.$field ?? [])])
    setEditFallback(mapping?.$fallback ?? '')
  }

  function saveEdit() {
    if (!editingKey) return
    onUpdate((c) => {
      const newMappings = { ...c.field_mappings }
      const entry: { $field: string[]; $fallback?: string } = { $field: editPaths.filter(Boolean) }
      if (editFallback.trim()) {
        entry.$fallback = editFallback
      }
      newMappings[editingKey] = entry
      return { ...c, field_mappings: newMappings }
    })
    setEditingKey(null)
  }

  function cancelEdit() {
    setEditingKey(null)
  }

  function addMapping() {
    const key = prompt('Field mapping key (e.g., my_field):')
    if (!key?.trim()) return
    onUpdate((c) => ({
      ...c,
      field_mappings: {
        ...c.field_mappings,
        [key.trim()]: { $field: ['SOURCE.path'] },
      },
    }))
  }

  function removeMapping(key: string) {
    onUpdate((c) => {
      const newMappings = { ...c.field_mappings }
      delete newMappings[key]
      return { ...c, field_mappings: newMappings }
    })
  }

  function movePath(idx: number, direction: -1 | 1) {
    const newPaths = [...editPaths]
    const swapIdx = idx + direction
    if (swapIdx < 0 || swapIdx >= newPaths.length) return
    ;[newPaths[idx], newPaths[swapIdx]] = [newPaths[swapIdx], newPaths[idx]]
    setEditPaths(newPaths)
  }

  function addPath() {
    setEditPaths([...editPaths, ''])
  }

  function removePath(idx: number) {
    setEditPaths(editPaths.filter((_, i) => i !== idx))
  }

  function updatePath(idx: number, value: string) {
    const newPaths = [...editPaths]
    newPaths[idx] = value
    setEditPaths(newPaths)
  }

  return (
    <div className="p-5 max-w-5xl">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-sm font-semibold text-zinc-900">Field Mappings</h2>
          <p className="text-xs text-zinc-400 mt-0.5">
            Each field resolves from a priority chain of source paths. First non-null value wins.
          </p>
        </div>
        <button
          onClick={addMapping}
          className="px-3 py-1.5 rounded-md bg-accent text-white text-xs font-medium hover:bg-accent-dark transition-colors"
        >
          + Add Mapping
        </button>
      </div>

      <div className="glow-border rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-zinc-100 bg-navy-700">
              <th className="py-3 px-4 text-left text-xs font-semibold text-zinc-400 uppercase tracking-wider w-56">
                Key
              </th>
              <th className="py-3 px-4 text-left text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                Source Paths (priority order)
              </th>
              <th className="py-3 px-4 text-left text-xs font-semibold text-zinc-400 uppercase tracking-wider w-48">
                Fallback
              </th>
              <th className="py-3 px-4 text-right text-xs font-semibold text-zinc-400 uppercase tracking-wider w-28">
                Actions
              </th>
            </tr>
          </thead>
          <tbody>
            {keys.map((key) => {
              const mapping = mappings[key]
              const paths = mapping?.$field ?? []
              const fallback = mapping?.$fallback
              const isEditing = editingKey === key

              return (
                <tr
                  key={key}
                  className="border-b border-zinc-50 hover:bg-accent-glow transition-colors align-top"
                >
                  <td className="py-2.5 px-4 font-mono text-xs text-zinc-700 font-medium">
                    {key}
                  </td>
                  <td className="py-2.5 px-4">
                    {isEditing ? (
                      <div className="space-y-1">
                        {editPaths.map((p, idx) => (
                          <div key={idx} className="flex items-center gap-1">
                            <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-blue-100 text-blue-700 text-[10px] font-bold flex-shrink-0">
                              {idx + 1}
                            </span>
                            <input
                              type="text"
                              value={p}
                              onChange={(e) => updatePath(idx, e.target.value)}
                              className="flex-1 px-2 py-0.5 rounded border border-zinc-200 text-xs font-mono bg-white focus:outline-none focus:ring-2 focus:ring-blue-300"
                            />
                            <button
                              onClick={() => movePath(idx, -1)}
                              disabled={idx === 0}
                              className="px-1 text-zinc-400 hover:text-zinc-700 disabled:opacity-30 text-xs"
                              title="Move up"
                            >
                              ^
                            </button>
                            <button
                              onClick={() => movePath(idx, 1)}
                              disabled={idx === editPaths.length - 1}
                              className="px-1 text-zinc-400 hover:text-zinc-700 disabled:opacity-30 text-xs"
                              title="Move down"
                            >
                              v
                            </button>
                            <button
                              onClick={() => removePath(idx)}
                              className="px-1 text-red-400 hover:text-red-600 text-xs"
                            >
                              x
                            </button>
                          </div>
                        ))}
                        <button
                          onClick={addPath}
                          className="text-[10px] text-blue-600 hover:text-blue-800 font-medium"
                        >
                          + Add source path
                        </button>
                      </div>
                    ) : (
                      <div
                        className="flex flex-wrap gap-1 cursor-pointer"
                        onDoubleClick={() => startEdit(key)}
                      >
                        {paths.map((p, idx) => (
                          <span
                            key={idx}
                            className="inline-flex items-center gap-1 rounded-full bg-blue-50 pl-1 pr-2 py-0.5 text-[11px] font-mono text-blue-700"
                          >
                            <span className="inline-flex items-center justify-center w-4 h-4 rounded-full bg-blue-200 text-blue-800 text-[9px] font-bold">
                              {idx + 1}
                            </span>
                            {p}
                          </span>
                        ))}
                      </div>
                    )}
                  </td>
                  <td className="py-2.5 px-4">
                    {isEditing ? (
                      <input
                        type="text"
                        value={editFallback}
                        onChange={(e) => setEditFallback(e.target.value)}
                        placeholder="(none)"
                        className="w-full px-2 py-0.5 rounded border border-zinc-200 text-xs font-mono bg-white focus:outline-none focus:ring-2 focus:ring-blue-300"
                      />
                    ) : (
                      <span
                        className="text-xs font-mono text-zinc-500 cursor-pointer hover:text-blue-600"
                        onDoubleClick={() => startEdit(key)}
                      >
                        {fallback ?? <span className="italic text-zinc-300">(none)</span>}
                      </span>
                    )}
                  </td>
                  <td className="py-2.5 px-4 text-right">
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
                          onClick={() => startEdit(key)}
                          className="px-2 py-0.5 rounded text-[10px] font-medium bg-zinc-100 text-zinc-600 hover:bg-zinc-200"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => removeMapping(key)}
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
            {keys.length === 0 && (
              <tr>
                <td colSpan={4} className="py-8 text-center text-sm text-zinc-400">
                  No field mappings. Click "Add Mapping" to create one.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
