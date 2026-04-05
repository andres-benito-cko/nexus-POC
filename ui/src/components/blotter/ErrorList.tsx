import type { PostingError } from '../../api/client'

interface Props {
  errors: PostingError[]
  isLoading: boolean
}

function formatDate(iso: string) {
  try { return new Date(iso).toLocaleString() } catch { return iso }
}

export default function ErrorList({ errors, isLoading }: Props) {
  return (
    <div className="glow-border rounded-xl overflow-hidden">
      {isLoading ? (
        <p className="p-5 text-sm text-zinc-400">Loading...</p>
      ) : errors.length === 0 ? (
        <div className="flex flex-col items-center py-10 text-zinc-400">
          <svg className="w-8 h-8 mb-2 text-emerald-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
              d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <p className="text-sm">No posting errors</p>
        </div>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-zinc-100">
              {['Nexus ID', 'Transaction ID', 'Currency', 'Debit Total', 'Credit Total', 'Rules Fired', 'When'].map((h) => (
                <th key={h} className="px-4 py-2.5 text-xs font-semibold text-zinc-400 uppercase tracking-wider text-left">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {errors.map((err) => (
              <tr key={err.id} className="border-b border-zinc-50 hover:bg-red-50/30 transition-colors">
                <td className="px-4 py-2.5 font-mono text-xs text-zinc-700">{err.nexusId}</td>
                <td className="px-4 py-2.5 font-mono text-xs text-zinc-600">{err.transactionId}</td>
                <td className="px-4 py-2.5 text-zinc-700 font-medium">{err.currency}</td>
                <td className="px-4 py-2.5 text-red-600 font-mono">{Number(err.debitTotal).toFixed(2)}</td>
                <td className="px-4 py-2.5 text-red-600 font-mono">{Number(err.creditTotal).toFixed(2)}</td>
                <td className="px-4 py-2.5 text-xs text-zinc-400 font-mono max-w-xs truncate">{err.ruleIds ?? '—'}</td>
                <td className="px-4 py-2.5 text-xs text-zinc-400">{formatDate(err.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
