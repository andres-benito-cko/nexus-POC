import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getDlq, replayDlq, type DlqEvent } from '../api/client'
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

function truncateErrors(errors: string[], maxLen = 60): string {
  const joined = errors.join('; ')
  return joined.length > maxLen ? joined.slice(0, maxLen) + '...' : joined
}

export default function DlqPage() {
  const queryClient = useQueryClient()
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [replayingId, setReplayingId] = useState<string | null>(null)

  const { data: events = [], isError } = useQuery({
    queryKey: ['dlq'],
    queryFn: getDlq,
  })

  async function handleReplay(id: string) {
    setReplayingId(id)
    try {
      await replayDlq(id)
      showToast('DLQ event replayed successfully', 'success')
      await queryClient.invalidateQueries({ queryKey: ['dlq'] })
    } catch {
      showToast('Failed to replay DLQ event', 'error')
    } finally {
      setReplayingId(null)
    }
  }

  return (
    <div className="max-w-6xl mx-auto fade-in">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-zinc-900">Dead Letter Queue</h1>
          <p className="text-sm text-zinc-500 mt-1">
            Events that failed processing. Review and replay when ready.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-sm text-zinc-400">{events.length} events</span>
          {isError && (
            <span className="inline-flex items-center rounded-full bg-red-50 px-2 py-0.5 text-[10px] font-medium text-red-600">
              Fetch error
            </span>
          )}
        </div>
      </div>

      <div className="glow-border rounded-xl overflow-hidden">
        {events.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-zinc-400">
            <svg
              className="w-10 h-10 mb-3 text-emerald-300"
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
            <p className="text-sm font-medium text-zinc-500">No DLQ events</p>
            <p className="text-xs text-zinc-400 mt-1">All events processed successfully</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-zinc-100 bg-navy-700">
                <th className="py-3 px-4 text-left text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                  ID
                </th>
                <th className="py-3 px-4 text-left text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                  Action ID
                </th>
                <th className="py-3 px-4 text-left text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                  Created At
                </th>
                <th className="py-3 px-4 text-left text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                  Errors
                </th>
                <th className="py-3 px-4 text-left text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                  Replayed At
                </th>
                <th className="py-3 px-4 text-right text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                  Action
                </th>
              </tr>
            </thead>
            <tbody>
              {events.map((ev: DlqEvent) => (
                <DlqRow
                  key={ev.id}
                  event={ev}
                  expanded={expandedId === ev.id}
                  replaying={replayingId === ev.id}
                  onToggle={() =>
                    setExpandedId(expandedId === ev.id ? null : ev.id)
                  }
                  onReplay={() => handleReplay(ev.id)}
                />
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

function DlqRow({
  event,
  expanded,
  replaying,
  onToggle,
  onReplay,
}: {
  event: DlqEvent
  expanded: boolean
  replaying: boolean
  onToggle: () => void
  onReplay: () => void
}) {
  const isReplayed = event.replayedAt !== null

  return (
    <>
      <tr
        className="border-b border-zinc-50 hover:bg-accent-glow transition-colors cursor-pointer"
        onClick={onToggle}
      >
        <td className="py-2.5 px-4 font-mono text-xs text-zinc-700">
          {truncateId(event.id)}
        </td>
        <td className="py-2.5 px-4 font-mono text-xs text-zinc-600">
          {truncateId(event.actionId, 12)}
        </td>
        <td className="py-2.5 px-4 text-zinc-500">{formatDate(event.createdAt)}</td>
        <td className="py-2.5 px-4 text-zinc-600 max-w-xs">
          {truncateErrors(event.errors)}
        </td>
        <td className="py-2.5 px-4">
          {isReplayed ? (
            <StatusBadge status="REPLAYED" />
          ) : (
            <span className="text-zinc-400">--</span>
          )}
        </td>
        <td className="py-2.5 px-4 text-right">
          {!isReplayed && (
            <button
              onClick={(e) => {
                e.stopPropagation()
                onReplay()
              }}
              disabled={replaying}
              className="px-3 py-1 rounded-md bg-accent text-white text-xs font-medium hover:bg-accent-dark transition-colors disabled:opacity-50"
            >
              {replaying ? 'Replaying...' : 'Replay'}
            </button>
          )}
        </td>
      </tr>
      {expanded && (
        <tr className="bg-navy-700">
          <td colSpan={6} className="px-4 py-4">
            <div className="space-y-3 fade-in">
              <div>
                <h4 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-1">
                  Errors
                </h4>
                <ul className="space-y-1">
                  {event.errors.map((err, i) => (
                    <li key={i} className="text-xs text-red-600 font-mono bg-red-50 rounded px-2 py-1">
                      {err}
                    </li>
                  ))}
                </ul>
              </div>
              <div>
                <h4 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-1">
                  Payload
                </h4>
                <pre className="text-xs text-zinc-700 bg-navy-800 rounded-lg p-3 overflow-x-auto border border-zinc-200">
                  {JSON.stringify(event.payload, null, 2)}
                </pre>
              </div>
            </div>
          </td>
        </tr>
      )}
    </>
  )
}
