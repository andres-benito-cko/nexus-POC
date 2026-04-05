import { useState } from 'react'
import type { LedgerEntry } from '../../api/client'

interface Props {
  entries: LedgerEntry[]
  isLoading: boolean
  onSelectEntry: (entry: LedgerEntry) => void
}

interface NexusGroup {
  nexusId: string
  entries: LedgerEntry[]
  ruleGroups: { ruleName: string; ruleId: string | null; entries: LedgerEntry[] }[]
  totalDebit: number
  totalCredit: number
  balanced: boolean
  latestTimestamp: string
}

function groupEntries(entries: LedgerEntry[]): NexusGroup[] {
  const byNexus = new Map<string, LedgerEntry[]>()
  for (const e of entries) {
    const list = byNexus.get(e.nexusId) ?? []
    list.push(e)
    byNexus.set(e.nexusId, list)
  }

  return [...byNexus.entries()]
    .map(([nexusId, nexusEntries]) => {
      const byRule = new Map<string, LedgerEntry[]>()
      for (const e of nexusEntries) {
        const key = e.ruleId ?? e.ruleName ?? 'unknown'
        const list = byRule.get(key) ?? []
        list.push(e)
        byRule.set(key, list)
      }

      const totalDebit = nexusEntries
        .filter((e) => e.side === 'DEBIT')
        .reduce((sum, e) => sum + Number(e.amount), 0)
      const totalCredit = nexusEntries
        .filter((e) => e.side === 'CREDIT')
        .reduce((sum, e) => sum + Number(e.amount), 0)

      return {
        nexusId,
        entries: nexusEntries,
        ruleGroups: [...byRule.entries()].map(([key, ruleEntries]) => ({
          ruleName: ruleEntries[0].ruleName ?? key,
          ruleId: ruleEntries[0].ruleId,
          entries: ruleEntries,
        })),
        totalDebit,
        totalCredit,
        balanced: Math.abs(totalDebit - totalCredit) < 0.000001,
        latestTimestamp: nexusEntries
          .map((e) => e.createdAt)
          .sort()
          .reverse()[0],
      }
    })
    .sort((a, b) => b.latestTimestamp.localeCompare(a.latestTimestamp))
}

function formatDate(iso: string) {
  try { return new Date(iso).toLocaleString() } catch { return iso }
}

function NexusAccordion({
  group,
  defaultOpen,
  onSelectEntry,
}: {
  group: NexusGroup
  defaultOpen: boolean
  onSelectEntry: (entry: LedgerEntry) => void
}) {
  const [open, setOpen] = useState(defaultOpen)

  return (
    <div className="glow-border rounded-xl overflow-hidden">
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center justify-between px-4 py-3 hover:bg-zinc-50 transition-colors text-left"
      >
        <div className="flex items-center gap-3">
          <svg
            className={`w-3.5 h-3.5 text-zinc-400 transition-transform ${open ? 'rotate-90' : ''}`}
            fill="none" stroke="currentColor" viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
          </svg>
          <span className="font-mono text-sm text-blue-600">{group.nexusId}</span>
          <span className="text-xs text-zinc-400">{formatDate(group.latestTimestamp)}</span>
          <span className="text-xs text-zinc-400">{group.entries.length} entries</span>
        </div>
        <div className="flex items-center gap-4">
          <span className="text-xs font-mono">
            <span className="text-amber-600">DR {group.totalDebit.toFixed(2)}</span>
            {' / '}
            <span className="text-emerald-600">CR {group.totalCredit.toFixed(2)}</span>
          </span>
          {group.balanced ? (
            <span className="text-emerald-500 text-sm" title="Balanced">&#10003;</span>
          ) : (
            <span className="text-red-500 text-sm" title="Unbalanced">&#10007;</span>
          )}
        </div>
      </button>

      {open && (
        <div className="px-4 pb-4 space-y-3">
          {group.ruleGroups.map((rg) => (
            <RuleSubGroup key={rg.ruleId ?? rg.ruleName} group={rg} onSelectEntry={onSelectEntry} />
          ))}
        </div>
      )}
    </div>
  )
}

function RuleSubGroup({
  group,
  onSelectEntry,
}: {
  group: { ruleName: string; entries: LedgerEntry[] }
  onSelectEntry: (entry: LedgerEntry) => void
}) {
  const [open, setOpen] = useState(true)

  return (
    <div className="border border-zinc-100 rounded-lg overflow-hidden">
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center gap-2 px-3 py-2 bg-zinc-50 hover:bg-zinc-100 transition-colors text-left"
      >
        <svg
          className={`w-3 h-3 text-zinc-400 transition-transform ${open ? 'rotate-90' : ''}`}
          fill="none" stroke="currentColor" viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        </svg>
        <span className="text-xs font-medium text-zinc-700">{group.ruleName}</span>
        <span className="text-[11px] text-zinc-400">({group.entries.length})</span>
      </button>
      {open && (
        <div className="divide-y divide-zinc-50">
          {group.entries.map((entry) => (
            <div
              key={entry.id}
              onClick={() => onSelectEntry(entry)}
              className="flex items-center justify-between px-3 py-2 pl-8 cursor-pointer hover:bg-blue-50/50 transition-colors"
            >
              <div className="flex items-center gap-3 text-xs">
                <span className={`font-semibold ${entry.side === 'DEBIT' ? 'text-amber-600' : 'text-emerald-600'}`}>
                  {entry.side === 'DEBIT' ? 'DR' : 'CR'}
                </span>
                <span className="text-zinc-700 font-medium">{entry.account}</span>
              </div>
              <span className="text-xs font-mono text-zinc-700">
                {Number(entry.amount).toFixed(2)} {entry.currency}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default function JournalView({ entries, isLoading, onSelectEntry }: Props) {
  const groups = groupEntries(entries)

  if (isLoading) return <p className="text-sm text-zinc-400 p-5">Loading...</p>
  if (groups.length === 0) return (
    <div className="glow-border rounded-xl p-5">
      <div className="flex flex-col items-center py-10 text-zinc-400">
        <p className="text-sm">No journal entries</p>
      </div>
    </div>
  )

  return (
    <div className="space-y-3">
      {groups.map((group, i) => (
        <NexusAccordion
          key={group.nexusId}
          group={group}
          defaultOpen={i === 0}
          onSelectEntry={onSelectEntry}
        />
      ))}
    </div>
  )
}
