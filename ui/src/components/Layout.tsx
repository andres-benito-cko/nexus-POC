import { NavLink, Outlet, Link } from 'react-router-dom'
import { useWebSocket } from '../hooks/useWebSocket'
import ToastContainer from './Toast'

const NAV_SECTIONS = [
  {
    label: 'Knowledge',
    items: [
      { to: '/knowledge/schema', label: 'Schema' },
      { to: '/knowledge/examples', label: 'Examples' },
      { to: '/knowledge/le-vs-nexus', label: 'LE vs Nexus' },
    ],
  },
  {
    label: 'Nexus POC',
    items: [
      { to: '/nexus/config', label: 'Config' },
      { to: '/nexus/test-bench', label: 'Test Bench' },
      { to: '/nexus/live', label: 'Live' },
      { to: '/nexus/dlq', label: 'DLQ' },
    ],
  },
  {
    label: 'Rules Engine POC',
    items: [
      { to: '/rules-engine/accounts', label: 'Accounts' },
      { to: '/rules-engine/rules', label: 'Rules' },
      { to: '/rules-engine/blotter', label: 'Blotter' },
    ],
  },
] as const

export default function Layout() {
  const { connected } = useWebSocket()

  return (
    <div className="flex min-h-screen bg-zinc-50">
      {/* Sidebar */}
      <aside className="fixed top-0 left-0 h-full w-56 bg-white border-r border-zinc-200 flex flex-col z-50">
        {/* Logo */}
        <Link
          to="/nexus"
          className="flex items-center gap-3 px-5 py-5 border-b border-zinc-100"
        >
          <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center flex-shrink-0">
            <svg
              className="w-4 h-4 text-white"
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
          <span className="text-base font-semibold text-zinc-900 tracking-tight">
            Nexus
          </span>
        </Link>

        {/* Nav sections */}
        <nav className="flex-1 overflow-y-auto py-4">
          {NAV_SECTIONS.map((section) => (
            <div key={section.label} className="mb-5">
              <p className="px-5 mb-1 text-[11px] font-semibold tracking-wider uppercase text-zinc-400 select-none">
                {section.label}
              </p>
              <ul>
                {section.items.map(({ to, label }) => (
                  <li key={to}>
                    <NavLink
                      to={to}
                      className={({ isActive }) =>
                        `flex items-center pl-5 pr-4 py-2 text-sm transition-all ${
                          isActive
                            ? 'border-l-2 border-blue-500 bg-blue-50 text-blue-700 font-medium'
                            : 'border-l-2 border-transparent text-zinc-500 hover:text-zinc-800 hover:bg-zinc-50'
                        }`
                      }
                    >
                      {label}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>

        {/* WebSocket indicator */}
        <div className="px-5 py-4 border-t border-zinc-100 flex items-center gap-2 text-xs text-zinc-500">
          <span
            className={`inline-block w-2 h-2 rounded-full flex-shrink-0 ${
              connected
                ? 'bg-emerald-500 shadow-[0_0_5px_rgba(34,197,94,0.4)]'
                : 'bg-red-500 shadow-[0_0_6px_rgba(239,68,68,0.5)]'
            }`}
          />
          {connected ? 'Live' : 'Disconnected'}
        </div>
      </aside>

      {/* Main content */}
      <main className="ml-56 flex-1">
        <div className="p-6">
          <Outlet />
        </div>
      </main>

      <ToastContainer />
    </div>
  )
}
