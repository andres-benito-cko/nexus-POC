import { useState } from 'react'
import type { LedgerEntry } from '../../api/client'

interface Props {
  entries: LedgerEntry[]
  isLoading: boolean
  selectedId: string | null
  onSelect: (entry: LedgerEntry) => void
}

type SortKey = 'createdAt' | 'nexusId' | 'transactionId' | 'ruleName' | 'account' | 'side' | 'amount' | 'currency' | 'productType' | 'transactionType'
type SortDir = 'asc' | 'desc'

function formatDate(iso: string) {
  try { return new Date(iso).toLocaleString() } catch { return iso }
}

export default function EntryTable({ entries, isLoading, selectedId, onSelect }: Props) {
  const [sortKey, setSortKey] = useState<SortKey>('createdAt')
  const [sortDir, setSortDir] = useState<SortDir>('desc')

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    } else {
      setSortKey(key)
      setSortDir('asc')
    }
  }

  const sorted = [...entries].sort((a, b) => {
    const av = a[sortKey] ?? ''
    const bv = b[sortKey] ?? ''
    const cmp = typeof av === 'number' && typeof bv === 'number'
      ? av - bv
      : String(av).localeCompare(String(bv))
    return sortDir === 'asc' ? cmp : -cmp
  })

  const columns: { key: SortKey; label: string }[] = [
    { key: 'createdAt', label: 'Timestamp' },
    { key: 'nexusId', label: 'Nexus ID' },
    { key: 'transactionId', label: 'Transaction ID' },
    { key: 'ruleName', label: 'Rule' },
    { key: 'account', label: 'Account' },
    { key: 'side', label: 'Side' },
    { key: 'amount', label: 'Amount' },
    { key: 'currency', label: 'CCY' },
    { key: 'productType', label: 'Product' },
    { key: 'transactionType', label: 'Txn Type' },
  ]

  return (
    <div className="glow-border rounded-xl overflow-hidden">
      {isLoading ? (
        <p className="p-5 text-sm text-zinc-400">Loading...</p>
      ) : entries.length === 0 ? (
        <div className="flex flex-col items-center py-10 text-zinc-400">
          <p className="text-sm">No ledger entries</p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-zinc-100">
                {columns.map((col) => (
                  <th
                    key={col.key}
                    onClick={() => toggleSort(col.key)}
                    className="px-4 py-2.5 text-xs font-semibold text-zinc-400 uppercase tracking-wider text-left cursor-pointer hover:text-zinc-600 select-none"
                  >
                    {col.label}
                    {sortKey === col.key && (
                      <span className="ml-1">{sortDir === 'asc' ? '\u2191' : '\u2193'}</span>
                    )}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {sorted.map((entry) => (
                <tr
                  key={entry.id}
                  onClick={() => onSelect(entry)}
                  className={`border-b border-zinc-50 cursor-pointer transition-colors ${
                    selectedId === entry.id
                      ? 'bg-blue-50 border-l-2 border-l-blue-500'
                      : 'hover:bg-zinc-50'
                  }`}
                >
                  <td className="px-4 py-2.5 text-xs text-zinc-500">{formatDate(entry.createdAt)}</td>
                  <td className="px-4 py-2.5 font-mono text-xs text-blue-600">{entry.nexusId}</td>
                  <td className="px-4 py-2.5 font-mono text-xs text-zinc-600">{entry.transactionId ?? '—'}</td>
                  <td className="px-4 py-2.5 text-xs text-zinc-700">{entry.ruleName ?? '—'}</td>
                  <td className="px-4 py-2.5 text-xs text-zinc-700 font-medium">{entry.account}</td>
                  <td className={`px-4 py-2.5 text-xs font-semibold ${
                    entry.side === 'DEBIT' ? 'text-amber-600' : 'text-emerald-600'
                  }`}>
                    {entry.side === 'DEBIT' ? 'DR' : 'CR'}
                  </td>
                  <td className="px-4 py-2.5 text-xs font-mono text-zinc-700">{Number(entry.amount).toFixed(2)}</td>
                  <td className="px-4 py-2.5 text-xs text-zinc-500">{entry.currency}</td>
                  <td className="px-4 py-2.5 text-xs text-zinc-500">{entry.productType ?? '—'}</td>
                  <td className="px-4 py-2.5 text-xs text-zinc-500">{entry.transactionType ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
