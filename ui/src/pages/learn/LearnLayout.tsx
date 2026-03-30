import { NavLink, Outlet } from 'react-router-dom'

const SUB_NAV = [
  { to: '/learn/schema', label: 'Schema' },
  { to: '/learn/examples', label: 'Examples' },
  { to: '/learn/le-vs-nexus', label: 'LE vs Nexus' },
] as const

export default function LearnLayout() {
  return (
    <div>
      <div className="sticky top-14 z-40 -m-6 mb-0 border-b border-zinc-200 bg-white px-6">
        <div className="flex items-center gap-1">
          {SUB_NAV.map(({ to, label }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `px-4 py-3 text-sm font-medium transition-all border-b-2 ${
                  isActive
                    ? 'border-cyan-500 text-zinc-900'
                    : 'border-transparent text-zinc-500 hover:text-zinc-900'
                }`
              }
            >
              {label}
            </NavLink>
          ))}
        </div>
      </div>
      <div className="pt-6">
        <Outlet />
      </div>
    </div>
  )
}
