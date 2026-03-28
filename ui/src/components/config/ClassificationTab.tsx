import { useState } from 'react'
import type { NexusEngineConfigContent, ClassificationRule } from '../../pages/ConfigEditor'

interface ClassificationTabProps {
  content: NexusEngineConfigContent | null
  onUpdate: (updater: (c: NexusEngineConfigContent) => NexusEngineConfigContent) => void
}

export default function ClassificationTab({ content, onUpdate }: ClassificationTabProps) {
  const [editingIdx, setEditingIdx] = useState<number | null>(null)
  const [editCondition, setEditCondition] = useState('')
  const [editResult, setEditResult] = useState('')

  if (!content) {
    return <ParseError />
  }

  const rules = content.classification?.family ?? []

  function startEdit(idx: number) {
    const rule = rules[idx]
    setEditingIdx(idx)
    setEditCondition(rule.when ?? '')
    setEditResult(rule.result ?? rule.default ?? '')
  }

  function saveEdit() {
    if (editingIdx === null) return
    onUpdate((c) => {
      const newRules = [...(c.classification?.family ?? [])]
      const isDefault = !editCondition.trim()
      if (isDefault) {
        newRules[editingIdx] = { default: editResult }
      } else {
        newRules[editingIdx] = { when: editCondition, result: editResult }
      }
      return {
        ...c,
        classification: {
          ...c.classification,
          family: newRules,
        },
      }
    })
    setEditingIdx(null)
  }

  function cancelEdit() {
    setEditingIdx(null)
  }

  function addRule() {
    onUpdate((c) => {
      const newRules = [...(c.classification?.family ?? [])]
      newRules.push({ when: 'true', result: 'NEW_FAMILY' })
      return {
        ...c,
        classification: {
          ...c.classification,
          family: newRules,
        },
      }
    })
  }

  function removeRule(idx: number) {
    onUpdate((c) => {
      const newRules = [...(c.classification?.family ?? [])]
      newRules.splice(idx, 1)
      return {
        ...c,
        classification: {
          ...c.classification,
          family: newRules,
        },
      }
    })
  }

  return (
    <div className="p-5 max-w-5xl">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-sm font-semibold text-zinc-900">Family Classification Rules</h2>
          <p className="text-xs text-zinc-400 mt-0.5">
            Rules are evaluated top-to-bottom. First match wins.
          </p>
        </div>
        <button
          onClick={addRule}
          className="px-3 py-1.5 rounded-md bg-accent text-white text-xs font-medium hover:bg-accent-dark transition-colors"
        >
          + Add Rule
        </button>
      </div>

      <div className="glow-border rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-zinc-100 bg-navy-700">
              <th className="py-3 px-4 text-left text-xs font-semibold text-zinc-400 uppercase tracking-wider w-16">
                #
              </th>
              <th className="py-3 px-4 text-left text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                Condition (SpEL)
              </th>
              <th className="py-3 px-4 text-left text-xs font-semibold text-zinc-400 uppercase tracking-wider w-48">
                Result (Family)
              </th>
              <th className="py-3 px-4 text-left text-xs font-semibold text-zinc-400 uppercase tracking-wider w-24">
                Type
              </th>
              <th className="py-3 px-4 text-right text-xs font-semibold text-zinc-400 uppercase tracking-wider w-32">
                Actions
              </th>
            </tr>
          </thead>
          <tbody>
            {rules.map((rule: ClassificationRule, idx: number) => {
              const isDefault = 'default' in rule
              const isEditing = editingIdx === idx

              return (
                <tr
                  key={idx}
                  className="border-b border-zinc-50 hover:bg-accent-glow transition-colors"
                >
                  <td className="py-2.5 px-4 text-zinc-400 font-mono text-xs">{idx + 1}</td>
                  <td className="py-2.5 px-4">
                    {isEditing ? (
                      <input
                        type="text"
                        value={editCondition}
                        onChange={(e) => setEditCondition(e.target.value)}
                        placeholder="SpEL expression (leave empty for default)"
                        className="w-full px-2 py-1 rounded border border-zinc-200 text-xs font-mono bg-white focus:outline-none focus:ring-2 focus:ring-blue-300"
                      />
                    ) : (
                      <span
                        className="font-mono text-xs text-zinc-700 cursor-pointer hover:text-blue-600"
                        onDoubleClick={() => startEdit(idx)}
                      >
                        {isDefault ? (
                          <span className="italic text-zinc-400">always</span>
                        ) : (
                          rule.when
                        )}
                      </span>
                    )}
                  </td>
                  <td className="py-2.5 px-4">
                    {isEditing ? (
                      <input
                        type="text"
                        value={editResult}
                        onChange={(e) => setEditResult(e.target.value)}
                        className="w-full px-2 py-1 rounded border border-zinc-200 text-xs font-medium bg-white focus:outline-none focus:ring-2 focus:ring-blue-300"
                      />
                    ) : (
                      <span
                        className="inline-flex items-center rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-semibold text-blue-700 cursor-pointer hover:bg-blue-100"
                        onDoubleClick={() => startEdit(idx)}
                      >
                        {isDefault ? rule.default : rule.result}
                      </span>
                    )}
                  </td>
                  <td className="py-2.5 px-4">
                    <span
                      className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider ${
                        isDefault
                          ? 'bg-zinc-100 text-zinc-500'
                          : 'bg-purple-50 text-purple-600'
                      }`}
                    >
                      {isDefault ? 'default' : 'when'}
                    </span>
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
                          onClick={() => startEdit(idx)}
                          className="px-2 py-0.5 rounded text-[10px] font-medium bg-zinc-100 text-zinc-600 hover:bg-zinc-200"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => removeRule(idx)}
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
            {rules.length === 0 && (
              <tr>
                <td colSpan={5} className="py-8 text-center text-sm text-zinc-400">
                  No classification rules. Click "Add Rule" to create one.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function ParseError() {
  return (
    <div className="flex items-center justify-center h-64 text-sm text-red-500">
      Unable to parse YAML content. Fix syntax errors in the Raw YAML tab.
    </div>
  )
}
