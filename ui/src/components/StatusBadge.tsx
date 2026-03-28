interface StatusBadgeProps {
  status: string
  className?: string
}

const STATUS_STYLES: Record<string, string> = {
  LIVE: 'bg-emerald-100 text-emerald-700',
  NOT_LIVE: 'bg-amber-100 text-amber-700',
  PREDICTED: 'bg-purple-100 text-purple-700',
  ACTUAL: 'bg-blue-100 text-blue-700',
  INITIATED: 'bg-zinc-100 text-zinc-600',
  CAPTURED: 'bg-blue-100 text-blue-700',
  SETTLED: 'bg-emerald-100 text-emerald-700',
  ACTIVE: 'bg-emerald-100 text-emerald-700',
  INACTIVE: 'bg-zinc-100 text-zinc-500',
  REPLAYED: 'bg-blue-100 text-blue-700',
  ERROR: 'bg-red-100 text-red-700',
}

const DEFAULT_STYLE = 'bg-zinc-100 text-zinc-600'

export default function StatusBadge({ status, className = '' }: StatusBadgeProps) {
  const style = STATUS_STYLES[status] ?? DEFAULT_STYLE

  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider ${style} ${className}`}
    >
      {status}
    </span>
  )
}
