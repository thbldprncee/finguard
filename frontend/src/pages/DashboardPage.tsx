import { useQuery } from '@tanstack/react-query'
import { Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts'
import Layout from '../components/Layout'
import StatsCard from '../components/StatsCard'
import RiskBadge from '../components/RiskBadge'
import { fraudApi } from '../api/fraudApi'
import { transactionApi } from '../api/transactionApi'
import { useSSE } from '../hooks/useSSE'
import { useAuth } from '../context/AuthContext'
import { Wifi, WifiOff } from 'lucide-react'

const COLORS = { APPROVED: '#22c55e', REVIEW_REQUIRED: '#eab308', BLOCKED: '#ef4444', PENDING: '#6b7280' }

export default function DashboardPage() {
  const { hasRole } = useAuth()
  const isStaff = hasRole('ANALYST', 'ADMIN')

  const { data: transactions } = useQuery({
    queryKey: ['transactions'],
    queryFn: () => transactionApi.list(0, 100),
  })

  const { data: alerts } = useQuery({
    queryKey: ['alerts'],
    queryFn: () => fraudApi.getAlerts(0, 5),
    enabled: isStaff,
  })

  const { events, connected } = useSSE('/api/sse/events')

  const txList = transactions?.content ?? []

  const statusCounts = {
    APPROVED: txList.filter((t) => t.status === 'APPROVED').length,
    REVIEW_REQUIRED: txList.filter((t) => t.status === 'REVIEW_REQUIRED').length,
    BLOCKED: txList.filter((t) => t.status === 'BLOCKED').length,
    PENDING: txList.filter((t) => t.status === 'PENDING').length,
  }

  const pieData = Object.entries(statusCounts)
    .filter(([, v]) => v > 0)
    .map(([name, value]) => ({ name, value }))

  const recent = txList.slice(0, 8)

  return (
    <Layout>
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-2xl font-bold text-white">Dashboard</h1>
        <div className="flex items-center gap-2 text-sm">
          {connected
            ? <><Wifi size={16} className="text-green-400" /><span className="text-green-400">Live</span></>
            : <><WifiOff size={16} className="text-gray-500" /><span className="text-gray-500">Offline</span></>
          }
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatsCard title="Total Transactions" value={transactions?.totalElements ?? 0} color="blue" />
        <StatsCard title="Approved" value={statusCounts.APPROVED} color="green" />
        <StatsCard title="Under Review" value={statusCounts.REVIEW_REQUIRED} color="yellow" />
        <StatsCard title="Blocked" value={statusCounts.BLOCKED} color="red" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        <div className="bg-gray-900 rounded-xl p-5 border border-gray-800">
          <h2 className="text-base font-semibold text-white mb-4">Decision Distribution</h2>
          <ResponsiveContainer width="100%" height={200}>
            <PieChart>
              <Pie data={pieData} cx="50%" cy="50%" outerRadius={80} dataKey="value" label={({ name, value }) => `${name}: ${value}`}>
                {pieData.map(({ name }) => (
                  <Cell key={name} fill={COLORS[name as keyof typeof COLORS] ?? '#6b7280'} />
                ))}
              </Pie>
              <Tooltip formatter={(v, n) => [v, n]} />
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div className="bg-gray-900 rounded-xl p-5 border border-gray-800">
          <h2 className="text-base font-semibold text-white mb-4">Recent Activity (live)</h2>
          <div className="space-y-2 max-h-52 overflow-y-auto">
            {events.length === 0 && <p className="text-gray-500 text-sm">Waiting for events…</p>}
            {events.map((e, i) => (
              <div key={i} className="text-xs bg-gray-800 rounded px-3 py-2 flex justify-between">
                <span className="text-gray-300 font-mono">{e.type}</span>
                <span className="text-gray-500">{String(e.data.transactionId ?? '').slice(0, 8)}…</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-gray-900 rounded-xl p-5 border border-gray-800">
          <h2 className="text-base font-semibold text-white mb-4">Recent Transactions</h2>
          <div className="space-y-2">
            {recent.map((tx) => (
              <div key={tx.id} className="flex items-center justify-between py-2 border-b border-gray-800 last:border-0">
                <div>
                  <p className="text-sm text-white font-mono">{tx.id.slice(0, 12)}…</p>
                  <p className="text-xs text-gray-400">{tx.amount} {tx.currency} · {tx.country}</p>
                </div>
                <RiskBadge decision={tx.status} />
              </div>
            ))}
          </div>
        </div>

        {isStaff && <div className="bg-gray-900 rounded-xl p-5 border border-gray-800">
          <h2 className="text-base font-semibold text-white mb-4">Recent Alerts</h2>
          <div className="space-y-2">
            {(alerts?.content ?? []).map((alert) => (
              <div key={alert.id} className="flex items-center justify-between py-2 border-b border-gray-800 last:border-0">
                <div>
                  <p className="text-sm text-white font-mono">{alert.transactionId.slice(0, 12)}…</p>
                  <p className="text-xs text-gray-400 truncate max-w-xs">{alert.explanation}</p>
                </div>
                <RiskBadge decision={alert.decision} score={alert.riskScore} />
              </div>
            ))}
            {(alerts?.content ?? []).length === 0 && (
              <p className="text-gray-500 text-sm">No alerts</p>
            )}
          </div>
        </div>}
      </div>
    </Layout>
  )
}
