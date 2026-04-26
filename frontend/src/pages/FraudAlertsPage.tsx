import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import Layout from '../components/Layout'
import RiskBadge from '../components/RiskBadge'
import { fraudApi } from '../api/fraudApi'

export default function FraudAlertsPage() {
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['alerts', page],
    queryFn: () => fraudApi.getAlerts(page, 20),
  })

  return (
    <Layout>
      <h1 className="text-2xl font-bold text-white mb-6">Fraud Alerts</h1>

      <div className="bg-gray-900 rounded-xl border border-gray-800 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-800 text-gray-400 text-left">
              {['Transaction ID', 'Decision', 'Risk Score', 'Triggered Rules', 'Explanation', 'Analyzed At'].map((h) => (
                <th key={h} className="px-4 py-3 font-medium">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-500">Loading…</td></tr>
            )}
            {(data?.content ?? []).map((alert) => (
              <tr key={alert.id} className="border-b border-gray-800/50 hover:bg-gray-800/40">
                <td className="px-4 py-3 font-mono text-gray-300">{alert.transactionId.slice(0, 12)}…</td>
                <td className="px-4 py-3"><RiskBadge decision={alert.decision} /></td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <div className="w-16 bg-gray-700 rounded-full h-2">
                      <div
                        className={`h-2 rounded-full ${alert.riskScore >= 70 ? 'bg-red-500' : alert.riskScore >= 40 ? 'bg-yellow-500' : 'bg-green-500'}`}
                        style={{ width: `${alert.riskScore}%` }}
                      />
                    </div>
                    <span className="text-white font-mono">{alert.riskScore}</span>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-1">
                    {alert.triggeredRules.map((r) => (
                      <span key={r} className="text-xs bg-gray-700 text-gray-300 rounded px-2 py-0.5">{r}</span>
                    ))}
                  </div>
                </td>
                <td className="px-4 py-3 text-gray-400 max-w-xs truncate">{alert.explanation}</td>
                <td className="px-4 py-3 text-gray-500">{new Date(alert.analyzedAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-800 text-sm">
          <span className="text-gray-400">{data?.totalElements ?? 0} alerts</span>
          <div className="flex gap-2">
            <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}
              className="px-3 py-1 rounded bg-gray-800 text-gray-300 disabled:opacity-40">Prev</button>
            <button disabled={!data || page >= data.totalPages - 1} onClick={() => setPage((p) => p + 1)}
              className="px-3 py-1 rounded bg-gray-800 text-gray-300 disabled:opacity-40">Next</button>
          </div>
        </div>
      </div>
    </Layout>
  )
}
