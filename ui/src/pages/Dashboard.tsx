import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  getActiveConfig,
  getConfigs,
  getDlq,
  getTransactions,
  activateConfig,
  type EngineConfig,
  type NexusTransaction,
} from '../api/client'
import StatusBadge from '../components/StatusBadge'
import { showToast } from '../components/Toast'

function formatDate(iso: string | null): string {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return iso
  }
}

function truncateId(id: string, len = 8): string {
  return id.length > len ? id.slice(0, len) + '...' : id
}

export default function Dashboard() {
  const activeQuery = useQuery({
    queryKey: ['config', 'active'],
    queryFn: getActiveConfig,
  })

  const configsQuery = useQuery({
    queryKey: ['configs'],
    queryFn: getConfigs,
  })

  const dlqQuery = useQuery({
    queryKey: ['dlq'],
    queryFn: getDlq,
  })

  const txQuery = useQuery({
    queryKey: ['transactions'],
    queryFn: getTransactions,
  })

  const active = activeQuery.data
  const configs = configsQuery.data ?? []
  const dlqCount = dlqQuery.data?.length ?? 0
  const transactions = txQuery.data ?? []
  const recent = transactions.slice(0, 10)

  async function handleActivate(id: string) {
    try {
      await activateConfig(id)
      showToast('Config activated', 'success')
      await activeQuery.refetch()
      await configsQuery.refetch()
    } catch {
      showToast('Failed to activate config', 'error')
    }
  }

  return (
    <div className="max-w-6xl mx-auto space-y-6 fade-in">
      {/* Top cards row */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Engine Status */}
        <Card title="Engine Status" error={activeQuery.isError}>
          {active ? (
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <StatusBadge status="ACTIVE" />
                <span className="text-sm text-zinc-600">Config loaded</span>
              </div>
              <p className="text-xs text-zinc-400">
                Version: <span className="font-medium text-zinc-700">{active.version}</span>
              </p>
              <p className="text-xs text-zinc-400">
                Loaded: <span className="text-zinc-600">{formatDate(active.createdAt)}</span>
              </p>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <StatusBadge status="INACTIVE" />
              <span className="text-sm text-zinc-500">No active config</span>
            </div>
          )}
        </Card>

        {/* Active Config */}
        <Card title="Active Config" error={activeQuery.isError}>
          {active ? (
            <div className="space-y-1.5">
              <p className="text-lg font-semibold text-zinc-900">{active.version}</p>
              <p className="text-xs text-zinc-400">
                Created by: <span className="text-zinc-600">{active.createdBy}</span>
              </p>
              <p className="text-xs text-zinc-400">
                Valid from: <span className="text-zinc-600">{formatDate(active.validFrom)}</span>
              </p>
            </div>
          ) : (
            <p className="text-sm text-zinc-400">--</p>
          )}
        </Card>

        {/* DLQ Count */}
        <Card title="DLQ Events" error={dlqQuery.isError}>
          <p className="text-3xl font-bold text-zinc-900">{dlqCount}</p>
          <p className="text-xs text-zinc-400 mt-1">
            {dlqCount === 0 ? 'All clear' : 'Unresolved events'}
          </p>
        </Card>

        {/* Throughput */}
        <Card title="Buffer Size" error={txQuery.isError}>
          <p className="text-3xl font-bold text-zinc-900">{transactions.length}</p>
          <p className="text-xs text-zinc-400 mt-1">Transactions in memory</p>
        </Card>
      </div>

      {/* Configs table */}
      <div className="glow-border rounded-xl p-5">
        <h2 className="text-sm font-semibold text-zinc-900 uppercase tracking-wider mb-4">
          Available Configs
        </h2>
        {configs.length === 0 ? (
          <EmptyState message="No configs found" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-zinc-100">
                  <Th>Version</Th>
                  <Th>Created By</Th>
                  <Th>Valid From</Th>
                  <Th>Status</Th>
                  <Th align="right">Action</Th>
                </tr>
              </thead>
              <tbody>
                {configs.map((c: EngineConfig) => (
                  <tr
                    key={c.id}
                    className="border-b border-zinc-50 hover:bg-accent-glow transition-colors"
                  >
                    <td className="py-2.5 pr-4 font-medium text-zinc-900">{c.version}</td>
                    <td className="py-2.5 pr-4 text-zinc-600">{c.createdBy}</td>
                    <td className="py-2.5 pr-4 text-zinc-500">{formatDate(c.validFrom)}</td>
                    <td className="py-2.5 pr-4">
                      <StatusBadge status={c.active ? 'ACTIVE' : 'INACTIVE'} />
                    </td>
                    <td className="py-2.5 text-right">
                      {!c.active && (
                        <button
                          onClick={() => handleActivate(c.id)}
                          className="px-3 py-1 rounded-md bg-accent text-white text-xs font-medium hover:bg-accent-dark transition-colors"
                        >
                          Activate
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Recent transactions */}
      <div className="glow-border rounded-xl p-5">
        <h2 className="text-sm font-semibold text-zinc-900 uppercase tracking-wider mb-4">
          Recent Transactions
        </h2>
        {recent.length === 0 ? (
          <EmptyState message="No transactions yet" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-zinc-100">
                  <Th>Transaction ID</Th>
                  <Th>Status</Th>
                  <Th>Trade Family</Th>
                  <Th>Trade Type</Th>
                  <Th>Processed At</Th>
                </tr>
              </thead>
              <tbody>
                {recent.map((tx: NexusTransaction) => {
                  const trade = tx.trades?.[0]
                  return (
                    <tr
                      key={tx.transaction_id}
                      className="border-b border-zinc-50 hover:bg-accent-glow transition-colors"
                    >
                      <td className="py-2.5 pr-4 font-mono text-xs text-zinc-700">
                        {truncateId(tx.transaction_id ?? '', 12)}
                      </td>
                      <td className="py-2.5 pr-4">
                        <StatusBadge status={tx.status} />
                      </td>
                      <td className="py-2.5 pr-4 text-zinc-600">{trade?.trade_family ?? '--'}</td>
                      <td className="py-2.5 pr-4 text-zinc-600">{trade?.trade_type ?? '--'}</td>
                      <td className="py-2.5 text-zinc-500">{formatDate(tx.processed_at)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

// --- Reusable sub-components ---

function Card({
  title,
  children,
  error,
}: {
  title: string
  children: ReactNode
  error?: boolean
}) {
  return (
    <div className="glow-border rounded-xl p-5">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">{title}</h3>
        {error && (
          <span className="inline-flex items-center rounded-full bg-red-50 px-2 py-0.5 text-[10px] font-medium text-red-600">
            Error
          </span>
        )}
      </div>
      {children}
    </div>
  )
}

function Th({ children, align }: { children: ReactNode; align?: 'right' | 'left' }) {
  return (
    <th
      className={`pb-2 pr-4 text-xs font-semibold text-zinc-400 uppercase tracking-wider ${
        align === 'right' ? 'text-right' : 'text-left'
      }`}
    >
      {children}
    </th>
  )
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-10 text-zinc-400">
      <svg
        className="w-8 h-8 mb-2 text-zinc-300"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={1.5}
          d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
        />
      </svg>
      <p className="text-sm">{message}</p>
    </div>
  )
}
