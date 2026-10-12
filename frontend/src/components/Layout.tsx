import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { ShieldCheck, LayoutDashboard, ArrowLeftRight, AlertTriangle, FileText, LogOut } from 'lucide-react'
import type { UserRole } from '../types'

const navItems: { to: string; label: string; icon: typeof LayoutDashboard; roles?: UserRole[] }[] = [
  { to: '/dashboard',     label: 'Dashboard',     icon: LayoutDashboard },
  { to: '/transactions',  label: 'Transactions',  icon: ArrowLeftRight   },
  { to: '/alerts',        label: 'Fraud Alerts',  icon: AlertTriangle, roles: ['ANALYST', 'ADMIN'] },
  { to: '/audit',         label: 'Audit Log',     icon: FileText,      roles: ['ANALYST', 'ADMIN'] },
]

export default function Layout({ children }: { children: React.ReactNode }) {
  const { user, logout, hasRole } = useAuth()
  const location = useLocation()

  return (
    <div className="flex min-h-screen bg-gray-950">
      <aside className="w-60 bg-gray-900 border-r border-gray-800 flex flex-col">
        <div className="p-5 border-b border-gray-800 flex items-center gap-2">
          <ShieldCheck className="text-blue-500" size={28} />
          <span className="text-xl font-bold text-white">FinGuard</span>
        </div>
        <nav className="flex-1 p-4 space-y-1">
          {navItems.filter(({ roles }) => !roles || hasRole(...roles)).map(({ to, label, icon: Icon }) => {
            const active = location.pathname.startsWith(to)
            return (
              <Link
                key={to}
                to={to}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors ${
                  active
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-400 hover:bg-gray-800 hover:text-white'
                }`}
              >
                <Icon size={18} />
                {label}
              </Link>
            )
          })}
        </nav>
        <div className="p-4 border-t border-gray-800">
          <div className="text-xs text-gray-500 mb-1">{user.email}</div>
          <div className="text-xs text-blue-400 mb-3 font-medium">{user.role}</div>
          <button
            onClick={logout}
            className="flex items-center gap-2 text-sm text-gray-400 hover:text-red-400 transition-colors"
          >
            <LogOut size={16} />
            Sign out
          </button>
        </div>
      </aside>
      <main className="flex-1 p-8 overflow-auto">{children}</main>
    </div>
  )
}
