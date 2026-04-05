import { useEffect, useRef } from 'react'
import { useWebSocket } from '../../hooks/useWebSocket'

interface Props {
  onRefresh: () => void
}

export default function NewEntriesBanner({ onRefresh }: Props) {
  const { messages } = useWebSocket()
  const baselineRef = useRef(0)
  const initializedRef = useRef(false)

  const ledgerMessages = messages.filter(
    (msg) => (msg as Record<string, unknown>)?.type === 'LEDGER_ENTRY'
  )

  // Set baseline on first render to ignore pre-existing messages
  useEffect(() => {
    if (!initializedRef.current) {
      baselineRef.current = ledgerMessages.length
      initializedRef.current = true
    }
  }, [ledgerMessages.length])

  const newCount = ledgerMessages.length - baselineRef.current

  const handleRefresh = () => {
    baselineRef.current = ledgerMessages.length
    onRefresh()
  }

  if (newCount <= 0) return null

  return (
    <div
      onClick={handleRefresh}
      className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg cursor-pointer hover:bg-blue-700 transition-colors text-sm"
    >
      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
          d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
      </svg>
      {newCount} new {newCount === 1 ? 'entry' : 'entries'} available
    </div>
  )
}
