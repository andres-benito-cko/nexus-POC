import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import Editor from '@monaco-editor/react'
import { getBlock, getBlockSource } from '../../api/client'
import TransactionTrace from '../TransactionTrace'

interface Props {
  nexusId: string
  initialView: 'block' | 'source'
  onClose: () => void
}

export default function TraceDrawer({ nexusId, initialView, onClose }: Props) {
  const [view, setView] = useState(initialView)

  const blockQuery = useQuery({
    queryKey: ['block', nexusId],
    queryFn: () => getBlock(nexusId),
  })

  const sourceQuery = useQuery({
    queryKey: ['block-source', nexusId],
    queryFn: () => getBlockSource(nexusId),
    enabled: view === 'source',
  })

  const blockData = blockQuery.data
  const parsedBlock = blockData?.rawJson ? (() => {
    try { return JSON.parse(blockData.rawJson) } catch { return null }
  })() : null

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/30"
        onClick={onClose}
      />

      {/* Drawer */}
      <div className="relative w-[60vw] max-w-4xl bg-white shadow-xl flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-zinc-200">
          <div className="flex items-center gap-3">
            <button
              onClick={() => setView('block')}
              className={`text-sm font-medium px-2 py-1 rounded transition-colors ${
                view === 'block' ? 'text-blue-600 bg-blue-50' : 'text-zinc-500 hover:text-zinc-700'
              }`}
            >
              Nexus Block
            </button>
            <span className="text-zinc-300">/</span>
            <button
              onClick={() => setView('source')}
              className={`text-sm font-medium px-2 py-1 rounded transition-colors ${
                view === 'source' ? 'text-emerald-600 bg-emerald-50' : 'text-zinc-500 hover:text-zinc-700'
              }`}
            >
              LE Message
            </button>
          </div>
          <button
            onClick={onClose}
            className="text-zinc-400 hover:text-zinc-600 transition-colors"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-5">
          {view === 'block' && (
            blockQuery.isLoading ? (
              <p className="text-sm text-zinc-400">Loading block...</p>
            ) : parsedBlock ? (
              <TransactionTrace data={parsedBlock} />
            ) : (
              <p className="text-sm text-zinc-400">Block not found</p>
            )
          )}

          {view === 'source' && (
            sourceQuery.isLoading ? (
              <p className="text-sm text-zinc-400">Loading LE message...</p>
            ) : sourceQuery.data ? (
              <div className="h-[calc(100vh-120px)] rounded-lg overflow-hidden border border-zinc-200">
                <Editor
                  defaultLanguage="json"
                  value={(() => {
                    try { return JSON.stringify(JSON.parse(sourceQuery.data), null, 2) } catch { return sourceQuery.data }
                  })()}
                  theme="vs-dark"
                  options={{
                    readOnly: true,
                    minimap: { enabled: false },
                    scrollBeyondLastLine: false,
                    fontSize: 12,
                    lineNumbers: 'on',
                    wordWrap: 'on',
                  }}
                />
              </div>
            ) : sourceQuery.isError ? (
              <p className="text-sm text-zinc-400">LE source not available for this block</p>
            ) : null
          )}
        </div>
      </div>
    </div>
  )
}
