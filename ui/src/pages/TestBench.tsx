import { useState, useRef } from 'react'
import { useMutation } from '@tanstack/react-query'
import Editor, { type OnMount } from '@monaco-editor/react'
import { runTestBench, type TestBenchResult } from '../api/client'
import TransactionTrace, { type TransactionData } from '../components/TransactionTrace'

const LE_EVENT_TEMPLATE = JSON.stringify(
  {
    id: 'test-001',
    actionId: 'act-test-001',
    actionRootId: 'pay-test-001',
    transactionVersion: 1,
    gatewayEvents: [
      {
        eventType: 'payment_captured',
        processedOn: '2024-01-15T10:00:00Z',
        amount: { value: 150.0, currencyCode: 'EUR' },
        acquirerName: 'CKO_UK_LTD',
        acquirerCountry: 'GB',
      },
    ],
    balancesChangedEvents: [],
    cosEvents: [],
    schemeSettlementEvents: [],
    cashEvents: [],
  },
  null,
  2
)

export default function TestBench() {
  const [editorValue, setEditorValue] = useState(LE_EVENT_TEMPLATE)
  const editorRef = useRef<Parameters<OnMount>[0] | null>(null)

  const mutation = useMutation<TestBenchResult, Error, unknown>({
    mutationFn: runTestBench,
  })

  const [parseError, setParseError] = useState<string | null>(null)

  function handleRunClick() {
    setParseError(null)
    try {
      const parsed = JSON.parse(editorValue)
      mutation.mutate(parsed)
    } catch (e) {
      setParseError(e instanceof Error ? e.message : 'Invalid JSON in editor')
    }
  }

  function handleReset() {
    setEditorValue(LE_EVENT_TEMPLATE)
    setParseError(null)
    mutation.reset()
    if (editorRef.current) {
      editorRef.current.setValue(LE_EVENT_TEMPLATE)
    }
  }

  const result = mutation.data
  const isLoading = mutation.isPending
  const apiError = mutation.error

  return (
    <div className="max-w-7xl mx-auto fade-in">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-zinc-900">Test Bench</h1>
          <p className="text-sm text-zinc-500 mt-1">
            Send a Linking Engine event through the Nexus engine and inspect the output.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left column - Input */}
        <div className="space-y-4">
          <div className="glow-border rounded-xl overflow-hidden">
            <div className="flex items-center justify-between px-4 py-2 bg-navy-700 border-b border-zinc-100">
              <h3 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                LE Event Input
              </h3>
              <div className="flex items-center gap-2">
                <button
                  onClick={handleReset}
                  className="px-3 py-1 rounded-md text-xs font-medium text-zinc-500 hover:text-zinc-700 hover:bg-zinc-100 transition-colors"
                >
                  Reset
                </button>
                <button
                  onClick={handleRunClick}
                  disabled={isLoading}
                  className="px-4 py-1 rounded-md bg-accent text-white text-xs font-medium hover:bg-accent-dark transition-colors disabled:opacity-50 flex items-center gap-1.5"
                >
                  {isLoading && (
                    <svg
                      className="animate-spin h-3 w-3 text-white"
                      xmlns="http://www.w3.org/2000/svg"
                      fill="none"
                      viewBox="0 0 24 24"
                    >
                      <circle
                        className="opacity-25"
                        cx="12"
                        cy="12"
                        r="10"
                        stroke="currentColor"
                        strokeWidth="4"
                      />
                      <path
                        className="opacity-75"
                        fill="currentColor"
                        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                      />
                    </svg>
                  )}
                  {isLoading ? 'Running...' : 'Run'}
                </button>
              </div>
            </div>
            <Editor
              height="500px"
              language="json"
              theme="vs-dark"
              value={editorValue}
              onChange={(value) => setEditorValue(value ?? '')}
              onMount={(editor) => {
                editorRef.current = editor
              }}
              options={{
                minimap: { enabled: false },
                fontSize: 13,
                lineNumbers: 'on',
                scrollBeyondLastLine: false,
                automaticLayout: true,
                tabSize: 2,
                wordWrap: 'on',
              }}
            />
          </div>
        </div>

        {/* Right column - Output */}
        <div className="space-y-4">
          {/* Error states */}
          {parseError && (
            <div className="rounded-xl border border-red-200 bg-red-50 p-4">
              <h3 className="text-sm font-semibold text-red-700 mb-1">JSON Parse Error</h3>
              <p className="text-xs text-red-600 font-mono">{parseError}</p>
            </div>
          )}

          {apiError && !parseError && (
            <div className="rounded-xl border border-red-200 bg-red-50 p-4">
              <h3 className="text-sm font-semibold text-red-700 mb-1">API Error</h3>
              <p className="text-xs text-red-600 font-mono">{apiError.message}</p>
            </div>
          )}

          {/* Result: error from engine */}
          {result && !result.success && (
            <div className="rounded-xl border border-red-200 bg-red-50 p-4 space-y-2">
              <h3 className="text-sm font-semibold text-red-700">Transformation Failed</h3>
              <ul className="space-y-1">
                {(result.errors ?? []).map((err, i) => (
                  <li key={i} className="text-xs text-red-600 font-mono bg-red-100 rounded px-2 py-1">
                    {err}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Result: success */}
          {result && result.success && result.transaction && (
            <>
              <div className="glow-border rounded-xl overflow-hidden">
                <div className="flex items-center justify-between px-4 py-2 bg-navy-700 border-b border-zinc-100">
                  <h3 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                    Nexus Block Output
                  </h3>
                  <span className="inline-flex items-center rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-700">
                    SUCCESS
                  </span>
                </div>
                <Editor
                  height="300px"
                  language="json"
                  theme="vs-dark"
                  value={JSON.stringify(result.transaction, null, 2)}
                  options={{
                    readOnly: true,
                    minimap: { enabled: false },
                    fontSize: 12,
                    lineNumbers: 'on',
                    scrollBeyondLastLine: false,
                    automaticLayout: true,
                    tabSize: 2,
                    wordWrap: 'on',
                  }}
                />
              </div>

              <div className="glow-border rounded-xl p-4">
                <h3 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-3">
                  Transaction Trace
                </h3>
                <TransactionTrace data={result.transaction as TransactionData} />
              </div>
            </>
          )}

          {/* Empty state */}
          {!result && !parseError && !apiError && !isLoading && (
            <div className="glow-border rounded-xl flex flex-col items-center justify-center py-20 text-zinc-400">
              <svg
                className="w-10 h-10 mb-3 text-zinc-300"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z"
                />
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
              <p className="text-sm font-medium text-zinc-500">Ready to run</p>
              <p className="text-xs text-zinc-400 mt-1">
                Edit the LE event on the left and click Run
              </p>
            </div>
          )}

          {/* Loading */}
          {isLoading && (
            <div className="glow-border rounded-xl flex flex-col items-center justify-center py-20 text-zinc-400">
              <svg
                className="animate-spin h-8 w-8 text-accent mb-3"
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
              >
                <circle
                  className="opacity-25"
                  cx="12"
                  cy="12"
                  r="10"
                  stroke="currentColor"
                  strokeWidth="4"
                />
                <path
                  className="opacity-75"
                  fill="currentColor"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                />
              </svg>
              <p className="text-sm font-medium text-zinc-500">Processing...</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
