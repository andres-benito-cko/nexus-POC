import { useState } from 'react'
import StatusBadge from './StatusBadge'

// Field names match the API's snake_case serialization
interface Leg {
  leg_type?: string
  leg_amount?: number
  leg_currency?: string
  leg_status?: string
  from_party?: { party_type?: string; party_id?: string }
  to_party?: { party_type?: string; party_id?: string }
  fees?: Fee[]
  [key: string]: unknown
}

interface Fee {
  fee_type?: string
  fee_amount?: number
  fee_currency?: string
  [key: string]: unknown
}

interface Transaction {
  transaction_id?: string
  product_type?: string
  transaction_type?: string
  transaction_status?: string
  legs?: Leg[]
  [key: string]: unknown
}

export interface TransactionData {
  nexus_id?: string
  status?: string
  processed_at?: string
  entity?: { id?: string }
  cko_entity_id?: string
  transactions?: Transaction[]
  [key: string]: unknown
}

function formatCurrency(amount?: number, currency?: string): string {
  if (amount === undefined || amount === null) return '--'
  const formatted = typeof amount === 'number' ? amount.toFixed(2) : String(amount)
  return currency ? `${formatted} ${currency}` : formatted
}

function CollapsibleSection({
  title,
  badge,
  defaultOpen = true,
  children,
}: {
  title: string
  badge?: string
  defaultOpen?: boolean
  children: React.ReactNode
}) {
  const [open, setOpen] = useState(defaultOpen)

  return (
    <div className="border border-zinc-100 rounded-lg overflow-hidden">
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center justify-between px-3 py-2 bg-navy-700 hover:bg-zinc-50 transition-colors text-left"
      >
        <div className="flex items-center gap-2">
          <svg
            className={`w-3.5 h-3.5 text-zinc-400 transition-transform ${open ? 'rotate-90' : ''}`}
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
          </svg>
          <span className="text-sm font-medium text-zinc-800">{title}</span>
        </div>
        {badge && <StatusBadge status={badge} />}
      </button>
      {open && <div className="px-3 py-3 space-y-2">{children}</div>}
    </div>
  )
}

function LegCard({ leg, index }: { leg: Leg; index: number }) {
  return (
    <div className="border border-zinc-100 rounded-md p-3 bg-navy-800 space-y-2">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-xs font-mono text-zinc-400">Leg {index + 1}</span>
          <span className="text-sm font-medium text-zinc-800">{leg.leg_type ?? 'UNKNOWN'}</span>
        </div>
        {leg.leg_status && <StatusBadge status={leg.leg_status} />}
      </div>

      <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
        <div>
          <span className="text-zinc-400">Amount: </span>
          <span className="text-zinc-700 font-medium">
            {formatCurrency(leg.leg_amount, leg.leg_currency)}
          </span>
        </div>
        <div>
          <span className="text-zinc-400">Flow: </span>
          <span className="text-zinc-700 font-medium">
            {leg.from_party?.party_type ?? '?'} &rarr; {leg.to_party?.party_type ?? '?'}
          </span>
        </div>
      </div>

      {leg.fees && leg.fees.length > 0 && (
        <div>
          <span className="text-[10px] uppercase tracking-wider font-semibold text-zinc-400">
            Fees ({leg.fees.length})
          </span>
          <div className="mt-1 space-y-1">
            {leg.fees.map((fee, fi) => (
              <div
                key={fi}
                className="flex items-center justify-between text-xs bg-white rounded px-2 py-1 border border-zinc-100"
              >
                <span className="text-zinc-600">{fee.fee_type ?? 'UNKNOWN'}</span>
                <span className="font-mono text-zinc-700">
                  {formatCurrency(fee.fee_amount, fee.fee_currency)}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export default function TransactionTrace({ data }: { data: TransactionData }) {
  const transactions = data.transactions ?? []

  return (
    <div className="space-y-4">
      {/* Transaction header */}
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-zinc-900">Transaction</h3>
          {data.status && <StatusBadge status={data.status} />}
        </div>

        <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
          {data.nexus_id && (
            <div>
              <span className="text-zinc-400">ID: </span>
              <span className="font-mono text-zinc-700">{data.nexus_id}</span>
            </div>
          )}
          {data.processed_at && (
            <div>
              <span className="text-zinc-400">Processed: </span>
              <span className="text-zinc-600">
                {new Date(data.processed_at).toLocaleString()}
              </span>
            </div>
          )}
          {data.entity?.id && (
            <div>
              <span className="text-zinc-400">Entity: </span>
              <span className="font-mono text-zinc-700">{data.entity.id}</span>
            </div>
          )}
          {data.cko_entity_id && (
            <div>
              <span className="text-zinc-400">CKO Entity: </span>
              <span className="font-mono text-zinc-700">{data.cko_entity_id}</span>
            </div>
          )}
        </div>
      </div>

      {/* Trades */}
      {transactions.length > 0 && (
        <div className="space-y-2">
          <h4 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">
            Trades ({transactions.length})
          </h4>
          {transactions.map((txn, ti) => (
            <CollapsibleSection
              key={txn.transaction_id ?? ti}
              title={`${txn.product_type ?? 'UNKNOWN'} / ${txn.transaction_type ?? 'UNKNOWN'}`}
              badge={txn.transaction_status}
              defaultOpen={ti === 0}
            >
              <div className="text-xs space-y-1 mb-2">
                {txn.transaction_id && (
                  <div>
                    <span className="text-zinc-400">Trade ID: </span>
                    <span className="font-mono text-zinc-700">{txn.transaction_id}</span>
                  </div>
                )}
              </div>

              {txn.legs && txn.legs.length > 0 ? (
                <div className="space-y-2">
                  {txn.legs.map((leg, li) => (
                    <LegCard key={li} leg={leg} index={li} />
                  ))}
                </div>
              ) : (
                <p className="text-xs text-zinc-400 italic">No legs</p>
              )}
            </CollapsibleSection>
          ))}
        </div>
      )}

      {transactions.length === 0 && (
        <p className="text-xs text-zinc-400 italic">No transaction data available</p>
      )}
    </div>
  )
}
