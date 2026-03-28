import { NavLink, Outlet } from 'react-router-dom'
import { useWebSocket } from '../hooks/useWebSocket'
import ToastContainer from './Toast'

const NAV_LINKS = [
  { to: '/', label: 'Dashboard' },
  { to: '/dlq', label: 'DLQ' },
  { to: '/config', label: 'Config' },
  { to: '/test-bench', label: 'Test Bench' },
  { to: '/live', label: 'Live' },
] as const

export default function Layout() {
  const { connected } = useWebSocket()

  return (
    <div className="min-h-screen bg-navy-800">
      {/* Top Nav */}
      <nav
        className="fixed top-0 left-0 right-0 z-50 border-b border-zinc-200"
        style={{
          background: 'rgba(255,255,255,0.98)',
          backdropFilter: 'blur(16px)',
          WebkitBackdropFilter: 'blur(16px)',
        }}
      >
        <div className="flex items-center justify-between px-6 h-14">
          {/* Logo */}
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center">
              <svg
                className="w-5 h-5 text-white"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M13 10V3L4 14h7v7l9-11h-7z"
                />
              </svg>
            </div>
            <span className="text-lg font-semibold text-zinc-900 tracking-tight">
              Nexus
            </span>
          </div>

          {/* Nav links */}
          <div className="flex items-center gap-1 bg-navy-900 rounded-lg p-1">
            {NAV_LINKS.map(({ to, label }) => (
              <NavLink
                key={to}
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  `px-4 py-1.5 rounded-md text-sm font-medium transition-all ${
                    isActive
                      ? 'text-zinc-900 bg-navy-600'
                      : 'text-zinc-500 hover:text-zinc-900'
                  }`
                }
              >
                {label}
              </NavLink>
            ))}
          </div>

          {/* WebSocket indicator */}
          <div className="flex items-center gap-2 text-xs text-zinc-500">
            <span
              className={`inline-block w-2 h-2 rounded-full ${
                connected
                  ? 'bg-emerald-500 shadow-[0_0_5px_rgba(34,197,94,0.4)]'
                  : 'bg-red-500 shadow-[0_0_6px_rgba(239,68,68,0.5)]'
              }`}
            />
            {connected ? 'Live' : 'Disconnected'}
          </div>
        </div>
      </nav>

      {/* Content */}
      <main className="pt-14">
        <div className="p-6">
          <Outlet />
        </div>
      </main>

      <ToastContainer />
    </div>
  )
}
