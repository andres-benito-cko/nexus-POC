import { useState, useRef, useEffect, useCallback } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import {
  getScenarios,
  playScenario,
  stopSimulator,
  type SimulatorScenario,
} from '../api/client'
import { useManualWebSocket, type WsMessage } from '../hooks/useManualWebSocket'
import TransactionTrace, { type TransactionData } from '../components/TransactionTrace'
import StatusBadge from '../components/StatusBadge'

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleTimeString()
  } catch {
    return iso
  }
}

function truncateId(id: string, len = 12): string {
  return id.length > len ? id.slice(0, len) + '...' : id
}

export default function LiveScreen() {
  const { connected, messages, connect, disconnect, clearMessages } = useManualWebSocket()
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null)
  const [selectedScenarioId, setSelectedScenarioId] = useState<string>('')
  const [autoScroll, setAutoScroll] = useState(true)
  const listRef = useRef<HTMLDivElement>(null)
  const listTopRef = useRef<HTMLDivElement>(null)

  // Fetch scenarios
  const scenariosQuery = useQuery<SimulatorScenario[]>({
    queryKey: ['scenarios'],
    queryFn: getScenarios,
  })

  const scenarios = scenariosQuery.data ?? []

  // Set default scenario when loaded
  useEffect(() => {
    if (scenarios.length > 0 && !selectedScenarioId) {
      setSelectedScenarioId(scenarios[0].id)
    }
  }, [scenarios, selectedScenarioId])

  // Simulator mutations
  const playMutation = useMutation({
    mutationFn: (id: string) => playScenario(id),
  })

  const stopMutation = useMutation({
    mutationFn: stopSimulator,
  })

  // Auto-scroll logic
  const handleScroll = useCallback(() => {
    if (!listRef.current) return
    const { scrollTop } = listRef.current
    // If user scrolled down (away from top, since newest is at top), disable auto-scroll
    setAutoScroll(scrollTop < 50)
  }, [])

  useEffect(() => {
    if (autoScroll && listTopRef.current) {
      listTopRef.current.scrollIntoView({ behavior: 'smooth' })
    }
  }, [messages.length, autoScroll])

  const selected = selectedIndex !== null ? messages[selectedIndex] : null
  const selectedPayload = selected?.payload as Record<string, unknown> | undefined

  return (
    <div className="max-w-7xl mx-auto fade-in">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-zinc-900">Live Stream</h1>
          <p className="text-sm text-zinc-500 mt-1">
            Real-time event stream from the Nexus engine.
          </p>
        </div>
      </div>

      {/* Top bar controls */}
      <div className="glow-border rounded-xl p-4 mb-6">
        <div className="flex flex-wrap items-center gap-4">
          {/* Connection controls */}
          <div className="flex items-center gap-2">
            <span
              className={`inline-block w-2.5 h-2.5 rounded-full ${
                connected
                  ? 'bg-emerald-500 shadow-[0_0_5px_rgba(34,197,94,0.4)]'
                  : 'bg-zinc-300'
              }`}
            />
            <span className="text-xs text-zinc-500">
              {connected ? 'Connected' : 'Disconnected'}
            </span>

            {!connected ? (
              <button
                onClick={connect}
                className="px-3 py-1 rounded-md bg-accent text-white text-xs font-medium hover:bg-accent-dark transition-colors"
              >
                Connect
              </button>
            ) : (
              <button
                onClick={disconnect}
                className="px-3 py-1 rounded-md bg-zinc-200 text-zinc-700 text-xs font-medium hover:bg-zinc-300 transition-colors"
              >
                Disconnect
              </button>
            )}

            <button
              onClick={() => {
                clearMessages()
                setSelectedIndex(null)
              }}
              className="px-3 py-1 rounded-md text-xs font-medium text-zinc-500 hover:text-zinc-700 hover:bg-zinc-100 transition-colors"
            >
              Clear
            </button>
          </div>

          {/* Separator */}
          <div className="h-6 w-px bg-zinc-200" />

          {/* Simulator controls */}
          <div className="flex items-center gap-2">
            <span className="text-xs text-zinc-400 font-medium">Simulator:</span>
            <select
              value={selectedScenarioId}
              onChange={(e) => setSelectedScenarioId(e.target.value)}
              className="text-xs border border-zinc-200 rounded-md px-2 py-1 text-zinc-700 bg-white focus:outline-none focus:ring-1 focus:ring-accent"
            >
              {scenarios.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.id}: {s.name}
                </option>
              ))}
              {scenarios.length === 0 && (
                <option disabled>Loading scenarios...</option>
              )}
            </select>

            <button
              onClick={() => {
                if (selectedScenarioId) playMutation.mutate(selectedScenarioId)
              }}
              disabled={!selectedScenarioId || playMutation.isPending}
              className="px-3 py-1 rounded-md bg-emerald-500 text-white text-xs font-medium hover:bg-emerald-600 transition-colors disabled:opacity-50"
            >
              {playMutation.isPending ? 'Playing...' : 'Play'}
            </button>

            <button
              onClick={() => stopMutation.mutate()}
              disabled={stopMutation.isPending}
              className="px-3 py-1 rounded-md bg-red-500 text-white text-xs font-medium hover:bg-red-600 transition-colors disabled:opacity-50"
            >
              Stop
            </button>
          </div>

          {/* Event count */}
          <div className="ml-auto text-xs text-zinc-400">
            {messages.length} events
          </div>
        </div>
      </div>

      {/* Main split panel */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6" style={{ minHeight: '600px' }}>
        {/* Left panel - Event stream (40%) */}
        <div className="lg:col-span-2 glow-border rounded-xl overflow-hidden flex flex-col">
          <div className="px-4 py-2 bg-navy-700 border-b border-zinc-100">
            <h3 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">
              Event Stream
            </h3>
          </div>

          <div
            ref={listRef}
            onScroll={handleScroll}
            className="flex-1 overflow-y-auto"
            style={{ maxHeight: '600px' }}
          >
            <div ref={listTopRef} />
            {messages.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-16 text-zinc-400">
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
                    d="M13 10V3L4 14h7v7l9-11h-7z"
                  />
                </svg>
                <p className="text-sm">
                  {connected ? 'Waiting for events...' : 'Connect to start streaming'}
                </p>
              </div>
            ) : (
              <div className="divide-y divide-zinc-50">
                {messages.map((msg, index) => (
                  <EventCard
                    key={`${msg.receivedAt}-${index}`}
                    message={msg}
                    isSelected={selectedIndex === index}
                    onClick={() => setSelectedIndex(index)}
                  />
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Right panel - Detail (60%) */}
        <div className="lg:col-span-3 glow-border rounded-xl overflow-hidden flex flex-col">
          <div className="px-4 py-2 bg-navy-700 border-b border-zinc-100">
            <h3 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">
              Event Detail
            </h3>
          </div>

          <div className="flex-1 overflow-y-auto p-4" style={{ maxHeight: '600px' }}>
            {!selected ? (
              <div className="flex flex-col items-center justify-center h-full text-zinc-400 py-16">
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
                    d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
                  />
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={1.5}
                    d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"
                  />
                </svg>
                <p className="text-sm">Select an event from the stream to inspect it</p>
              </div>
            ) : selected.type === 'NEXUS_TRANSACTION' ? (
              <NexusTransactionDetail payload={selectedPayload} />
            ) : selected.type === 'DLQ_EVENT' ? (
              <DlqEventDetail payload={selectedPayload} />
            ) : (
              <div className="space-y-3">
                <h3 className="text-sm font-semibold text-zinc-700">
                  Unknown Event: {selected.type}
                </h3>
                <pre className="text-xs text-zinc-700 bg-navy-800 rounded-lg p-3 overflow-x-auto border border-zinc-200">
                  {JSON.stringify(selected.payload, null, 2)}
                </pre>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

// --- Sub-components ---

function EventCard({
  message,
  isSelected,
  onClick,
}: {
  message: WsMessage
  isSelected: boolean
  onClick: () => void
}) {
  const payload = message.payload as Record<string, unknown> | undefined
  const actionId = (payload?.action_id as string) ?? (payload?.transaction_id as string) ?? '--'
  const status = (payload?.status as string) ?? ''

  const typeBadgeStyle =
    message.type === 'NEXUS_TRANSACTION'
      ? 'bg-blue-100 text-blue-700'
      : message.type === 'DLQ_EVENT'
        ? 'bg-red-100 text-red-700'
        : 'bg-zinc-100 text-zinc-600'

  return (
    <div
      onClick={onClick}
      className={`px-4 py-3 cursor-pointer transition-colors ${
        isSelected
          ? 'bg-accent-glow border-l-2 border-l-accent'
          : 'hover:bg-accent-glow border-l-2 border-l-transparent'
      }`}
    >
      <div className="flex items-center justify-between mb-1">
        <span
          className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider ${typeBadgeStyle}`}
        >
          {message.type === 'NEXUS_TRANSACTION'
            ? 'NEXUS'
            : message.type === 'DLQ_EVENT'
              ? 'DLQ'
              : message.type}
        </span>
        {status && <StatusBadge status={status} />}
      </div>
      <div className="flex items-center justify-between">
        <span className="text-xs font-mono text-zinc-600">{truncateId(String(actionId))}</span>
        <span className="text-[10px] text-zinc-400">{formatTime(message.receivedAt)}</span>
      </div>
    </div>
  )
}

function NexusTransactionDetail({
  payload,
}: {
  payload: Record<string, unknown> | undefined
}) {
  if (!payload) return null

  return (
    <div className="space-y-4 fade-in">
      <div className="flex items-center gap-2 mb-2">
        <span className="inline-flex items-center rounded-full bg-blue-100 px-2 py-0.5 text-[10px] font-semibold text-blue-700 uppercase tracking-wider">
          NEXUS_TRANSACTION
        </span>
      </div>
      <TransactionTrace data={payload as TransactionData} />
    </div>
  )
}

function DlqEventDetail({
  payload,
}: {
  payload: Record<string, unknown> | undefined
}) {
  const [showOriginal, setShowOriginal] = useState(false)

  if (!payload) return null

  const actionId = (payload.action_id as string) ?? '--'
  const errors = (payload.errors as string[]) ?? []
  const originalTransaction = payload.originalTransaction ?? payload.leTransaction ?? null

  return (
    <div className="space-y-4 fade-in">
      <div className="flex items-center gap-2 mb-2">
        <span className="inline-flex items-center rounded-full bg-red-100 px-2 py-0.5 text-[10px] font-semibold text-red-700 uppercase tracking-wider">
          DLQ_EVENT
        </span>
      </div>

      <div className="text-xs space-y-1">
        <div>
          <span className="text-zinc-400">Action ID: </span>
          <span className="font-mono text-zinc-700">{actionId}</span>
        </div>
      </div>

      {errors.length > 0 && (
        <div>
          <h4 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-2">
            Errors ({errors.length})
          </h4>
          <ul className="space-y-1">
            {errors.map((err, i) => (
              <li
                key={i}
                className="text-xs text-red-600 font-mono bg-red-50 rounded px-2 py-1 border border-red-100"
              >
                {err}
              </li>
            ))}
          </ul>
        </div>
      )}

      {originalTransaction && (
        <div>
          <button
            onClick={() => setShowOriginal(!showOriginal)}
            className="flex items-center gap-1.5 text-xs font-medium text-zinc-500 hover:text-zinc-700 transition-colors"
          >
            <svg
              className={`w-3.5 h-3.5 transition-transform ${showOriginal ? 'rotate-90' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M9 5l7 7-7 7"
              />
            </svg>
            Original LE Transaction
          </button>
          {showOriginal && (
            <pre className="mt-2 text-xs text-zinc-700 bg-navy-800 rounded-lg p-3 overflow-x-auto border border-zinc-200 fade-in">
              {JSON.stringify(originalTransaction, null, 2)}
            </pre>
          )}
        </div>
      )}
    </div>
  )
}
