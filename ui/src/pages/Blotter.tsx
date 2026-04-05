import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getLedgerEntries, getPostingErrors, getAccounts, type LedgerEntry } from '../api/client'
import EntryTable from '../components/blotter/EntryTable'
import JournalView from '../components/blotter/JournalView'
import ErrorList from '../components/blotter/ErrorList'
import BlotterFilterBar, { type BlotterFilters } from '../components/blotter/BlotterFilterBar'
import EntryDetailPanel from '../components/blotter/EntryDetailPanel'
import NewEntriesBanner from '../components/blotter/NewEntriesBanner'

const TABS = ['entries', 'journal', 'errors'] as const
type Tab = (typeof TABS)[number]

function filterEntries(entries: LedgerEntry[], filters: BlotterFilters): LedgerEntry[] {
  return entries.filter((e) => {
    if (filters.nexusId && !e.nexusId.toLowerCase().includes(filters.nexusId.toLowerCase())) return false
    if (filters.account && e.account !== filters.account) return false
    if (filters.currency && e.currency !== filters.currency) return false
    if (filters.side && e.side !== filters.side) return false
    if (filters.productType && e.productType !== filters.productType) return false
    if (filters.dateFrom && e.createdAt < filters.dateFrom) return false
    if (filters.dateTo && e.createdAt > filters.dateTo + 'T23:59:59') return false
    return true
  })
}

export default function Blotter() {
  const [searchParams, setSearchParams] = useSearchParams()
  const activeTab = (searchParams.get('tab') as Tab) || 'entries'
  const [filters, setFilters] = useState<BlotterFilters>({})
  const [selectedEntry, setSelectedEntry] = useState<LedgerEntry | null>(null)

  const setTab = (tab: Tab) => {
    setSearchParams({ tab })
    setSelectedEntry(null)
  }

  const entriesQuery = useQuery({
    queryKey: ['ledger-entries'],
    queryFn: () => getLedgerEntries({ limit: 500 }),
  })

  const errorsQuery = useQuery({
    queryKey: ['posting-errors'],
    queryFn: () => getPostingErrors(),
  })

  const accountsQuery = useQuery({
    queryKey: ['accounts'],
    queryFn: getAccounts,
  })

  const entries = entriesQuery.data ?? []
  const errors = errorsQuery.data ?? []
  const filtered = filterEntries(entries, filters)

  const errorCount = errors.length

  return (
    <div className="max-w-7xl mx-auto space-y-4 fade-in">
      <h1 className="text-xl font-semibold text-zinc-900">Blotter</h1>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-zinc-200">
        {TABS.map((tab) => (
          <button
            key={tab}
            onClick={() => setTab(tab)}
            className={`px-4 py-2.5 text-sm font-medium transition-colors relative ${
              activeTab === tab
                ? 'text-blue-600 after:absolute after:bottom-0 after:left-0 after:right-0 after:h-0.5 after:bg-blue-600'
                : 'text-zinc-500 hover:text-zinc-700'
            }`}
          >
            {tab.charAt(0).toUpperCase() + tab.slice(1)}
            {tab === 'errors' && errorCount > 0 && (
              <span className="ml-2 px-1.5 py-0.5 text-[11px] font-semibold bg-red-100 text-red-600 rounded-full">
                {errorCount}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Filter bar + new entries banner */}
      <BlotterFilterBar
        filters={filters}
        onChange={setFilters}
        accounts={accountsQuery.data ?? []}
        entries={entries}
      />
      <NewEntriesBanner onRefresh={() => entriesQuery.refetch()} />

      {/* Tab content + side panel */}
      <div className="flex gap-4">
        <div className={`flex-1 min-w-0 ${selectedEntry ? 'max-w-[calc(100%-360px)]' : ''}`}>
          {activeTab === 'entries' && (
            <EntryTable
              entries={filtered}
              isLoading={entriesQuery.isLoading}
              selectedId={selectedEntry?.id ?? null}
              onSelect={setSelectedEntry}
            />
          )}
          {activeTab === 'journal' && (
            <JournalView entries={filtered} isLoading={entriesQuery.isLoading} onSelectEntry={setSelectedEntry} />
          )}
          {activeTab === 'errors' && (
            <ErrorList errors={filters.nexusId ? errors.filter(e => e.nexusId.toLowerCase().includes(filters.nexusId!.toLowerCase())) : errors} isLoading={errorsQuery.isLoading} />
          )}
        </div>
        {selectedEntry && (
          <EntryDetailPanel entry={selectedEntry} onClose={() => setSelectedEntry(null)} />
        )}
      </div>
    </div>
  )
}
