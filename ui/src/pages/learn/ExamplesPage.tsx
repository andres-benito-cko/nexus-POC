import { useEffect, useRef, useState } from 'react'
import { LEARN_EXAMPLES, LearnExample } from '../../data/learnExamples'
import { runTestBench } from '../../api/client'
import TransactionTrace, { TransactionData } from '../../components/TransactionTrace'

function JsonPreview({ value }: { value: unknown }) {
  const json = JSON.stringify(value, null, 2)
  return (
    <pre className="flex-1 min-h-0 text-xs font-mono bg-zinc-50 border border-zinc-200 rounded p-3 overflow-auto">
      {json}
    </pre>
  )
}

function ScenarioCard({
  example,
  active,
  onClick,
}: {
  example: LearnExample
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      onClick={onClick}
      className={`w-full text-left px-3 py-2.5 rounded-md border transition-colors ${
        active
          ? 'bg-cyan-50 border-cyan-300 text-cyan-800'
          : 'border-zinc-200 bg-white hover:bg-zinc-50 text-zinc-800'
      }`}
    >
      <p className={`text-sm font-medium ${active ? 'text-cyan-900' : 'text-zinc-900'}`}>
        {example.title}
      </p>
      <p className={`text-xs mt-0.5 ${active ? 'text-cyan-700' : 'text-zinc-500'}`}>
        {example.subtitle}
      </p>
    </button>
  )
}

export default function ExamplesPage() {
  const [selected, setSelected] = useState<LearnExample | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<TransactionData | null>(null)
  const reqId = useRef(0)

  function selectScenario(example: LearnExample) {
    const id = ++reqId.current
    setSelected(example)
    setResult(null)
    setError(null)
    setLoading(true)

    runTestBench(example.lePayload)
      .then((res) => {
        if (id !== reqId.current) return
        setResult(res.transaction as TransactionData)
      })
      .catch((err: unknown) => {
        if (id !== reqId.current) return
        setError(err instanceof Error ? err.message : 'Unexpected error')
      })
      .finally(() => {
        if (id !== reqId.current) return
        setLoading(false)
      })
  }

  useEffect(() => {
    if (LEARN_EXAMPLES.length > 0) {
      const first = LEARN_EXAMPLES[0]
      const id = ++reqId.current
      setSelected(first)
      setResult(null)
      setError(null)
      setLoading(true)
      runTestBench(first.lePayload)
        .then((res) => { if (id === reqId.current) setResult(res.transaction as TransactionData) })
        .catch((err: unknown) => { if (id === reqId.current) setError(err instanceof Error ? err.message : 'Unexpected error') })
        .finally(() => { if (id === reqId.current) setLoading(false) })
    }
  }, [])

  return (
    <div className="flex gap-4 h-full min-h-0">
      {/* Sidebar */}
      <div className="w-56 flex-none flex flex-col gap-1.5 overflow-y-auto">
        {LEARN_EXAMPLES.map((ex) => (
          <ScenarioCard
            key={ex.id}
            example={ex}
            active={selected?.id === ex.id}
            onClick={() => selectScenario(ex)}
          />
        ))}
      </div>

      {/* Right panel */}
      <div className="flex-1 min-w-0 grid grid-cols-2 gap-4 overflow-hidden">
        {/* LE Event In */}
        <div className="flex flex-col min-h-0">
          <p className="text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-2">
            LE Event In
          </p>
          {selected ? (
            <JsonPreview value={selected.lePayload} />
          ) : (
            <p className="text-sm text-zinc-400 italic">Select a scenario</p>
          )}
        </div>

        {/* Nexus Block Out */}
        <div className="flex flex-col min-h-0 overflow-y-auto">
          <p className="text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-2">
            Nexus Block Out
          </p>

          {!selected && (
            <p className="text-sm text-zinc-400 italic">Select a scenario to see the output</p>
          )}

          {selected && loading && (
            <div className="flex items-center gap-2 text-sm text-zinc-500">
              <svg
                className="animate-spin w-4 h-4 text-cyan-500"
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
                  d="M4 12a8 8 0 018-8v8H4z"
                />
              </svg>
              Transforming...
            </div>
          )}

          {selected && !loading && error && (
            <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 whitespace-pre-wrap">
              {error}
            </div>
          )}

          {selected && !loading && result && (
            <TransactionTrace data={result} />
          )}
        </div>
      </div>
    </div>
  )
}
