import type { Account, LedgerEntry } from '../../api/client'

export interface BlotterFilters {
  nexusId?: string
  account?: string
  currency?: string
  side?: 'DEBIT' | 'CREDIT'
  productType?: string
  dateFrom?: string
  dateTo?: string
}

interface Props {
  filters: BlotterFilters
  onChange: (filters: BlotterFilters) => void
  accounts: Account[]
  entries: LedgerEntry[]
}

export default function BlotterFilterBar({ filters, onChange, accounts, entries }: Props) {
  const currencies = [...new Set(entries.map((e) => e.currency))].sort()
  const productTypes = [...new Set(entries.map((e) => e.productType).filter(Boolean))].sort()

  const update = (patch: Partial<BlotterFilters>) => onChange({ ...filters, ...patch })

  const selectClass =
    'bg-white border border-zinc-200 rounded-lg px-3 py-1.5 text-sm text-zinc-700 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-400'
  const inputClass = selectClass

  return (
    <div className="flex flex-wrap gap-2 items-center">
      <input
        type="text"
        placeholder="Search nexus ID..."
        value={filters.nexusId ?? ''}
        onChange={(e) => update({ nexusId: e.target.value || undefined })}
        className={`${inputClass} w-48`}
      />
      <select
        value={filters.account ?? ''}
        onChange={(e) => update({ account: e.target.value || undefined })}
        className={selectClass}
      >
        <option value="">All accounts</option>
        {accounts.map((a) => (
          <option key={a.code} value={a.code}>{a.code}</option>
        ))}
      </select>
      <select
        value={filters.side ?? ''}
        onChange={(e) => update({ side: (e.target.value as 'DEBIT' | 'CREDIT') || undefined })}
        className={selectClass}
      >
        <option value="">All sides</option>
        <option value="DEBIT">Debit</option>
        <option value="CREDIT">Credit</option>
      </select>
      <select
        value={filters.currency ?? ''}
        onChange={(e) => update({ currency: e.target.value || undefined })}
        className={selectClass}
      >
        <option value="">All currencies</option>
        {currencies.map((c) => (
          <option key={c} value={c}>{c}</option>
        ))}
      </select>
      <select
        value={filters.productType ?? ''}
        onChange={(e) => update({ productType: e.target.value || undefined })}
        className={selectClass}
      >
        <option value="">All products</option>
        {productTypes.map((p) => (
          <option key={p} value={p!}>{p}</option>
        ))}
      </select>
      <input
        type="date"
        value={filters.dateFrom ?? ''}
        onChange={(e) => update({ dateFrom: e.target.value || undefined })}
        className={inputClass}
        title="From date"
      />
      <input
        type="date"
        value={filters.dateTo ?? ''}
        onChange={(e) => update({ dateTo: e.target.value || undefined })}
        className={inputClass}
        title="To date"
      />
      {Object.values(filters).some(Boolean) && (
        <button
          onClick={() => onChange({})}
          className="text-xs text-zinc-400 hover:text-zinc-600 transition-colors"
        >
          Clear filters
        </button>
      )}
    </div>
  )
}
