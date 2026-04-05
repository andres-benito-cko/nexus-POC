import { useState } from 'react'
import type { LedgerEntry } from '../../api/client'
import TraceDrawer from './TraceDrawer'

interface Props {
  entry: LedgerEntry
  onClose: () => void
}

function formatDate(iso: string) {
  try { return new Date(iso).toLocaleString() } catch { return iso }
}

function Field({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div>
      <span className="text-[11px] text-zinc-400 uppercase tracking-wider">{label}</span>
      <p className="text-sm text-zinc-800 font-medium">{value ?? '—'}</p>
    </div>
  )
}

export default function EntryDetailPanel({ entry, onClose }: Props) {
  const [drawerView, setDrawerView] = useState<'block' | 'source' | null>(null)

  return (
    <>
      <div className="w-[350px] flex-shrink-0 glow-border rounded-xl p-4 space-y-4 h-fit sticky top-6">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-zinc-900">Entry Detail</h3>
          <button
            onClick={onClose}
            className="text-zinc-400 hover:text-zinc-600 transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Field label="Account" value={entry.account} />
          <Field
            label="Side"
            value={entry.side === 'DEBIT' ? 'DEBIT' : 'CREDIT'}
          />
          <Field label="Amount" value={`${Number(entry.amount).toFixed(2)} ${entry.currency}`} />
          <Field label="Rule" value={entry.ruleName} />
          <Field label="Nexus ID" value={entry.nexusId} />
          <Field label="Transaction ID" value={entry.transactionId} />
          <Field label="Leg ID" value={entry.legId} />
          <Field label="Product" value={entry.productType} />
          <Field label="Txn Type" value={entry.transactionType} />
          <Field label="Txn Status" value={entry.transactionStatus} />
          <div className="col-span-2">
            <Field label="Timestamp" value={formatDate(entry.createdAt)} />
          </div>
        </div>

        {/* Traceability */}
        <div className="border-t border-zinc-100 pt-3">
          <span className="text-[11px] text-zinc-400 uppercase tracking-wider">Traceability</span>
          <div className="mt-2 flex flex-col gap-2">
            <button
              onClick={() => setDrawerView('block')}
              className="flex items-center gap-2 px-3 py-2 rounded-lg bg-blue-50 text-blue-700 text-xs font-medium hover:bg-blue-100 transition-colors"
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              View Nexus Block
            </button>
            <button
              onClick={() => setDrawerView('source')}
              className="flex items-center gap-2 px-3 py-2 rounded-lg bg-emerald-50 text-emerald-700 text-xs font-medium hover:bg-emerald-100 transition-colors"
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
              </svg>
              View LE Message
            </button>
          </div>
        </div>
      </div>

      {drawerView && (
        <TraceDrawer
          nexusId={entry.nexusId}
          initialView={drawerView}
          onClose={() => setDrawerView(null)}
        />
      )}
    </>
  )
}
