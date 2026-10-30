import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Layout from '../components/Layout'
import RiskBadge from '../components/RiskBadge'
import { fraudApi } from '../api/fraudApi'

export default function FraudAlertsPage() {
  const [page, setPage] = useState(0)

  const qc = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['alerts', page],
    queryFn: () => fraudApi.getAlerts(page, 20),
  })

  const label = useMutation({
    mutationFn: ({ transactionId, fraud }: { transactionId: string; fraud: boolean }) =>
      fraudApi.label(transactionId, fraud),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['alerts'] }),
  })

  return (
    <Layout>
      <h1 className="text-2xl font-bold text-white mb-6">Fraud Alerts</h1>

      <div className="bg-gray-900 rounded-xl border border-gray-800 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-800 text-gray-400 text-left">
              {['Transaction ID', 'Decision', 'Risk Score', 'ML', 'Triggered Rules', 'Explanation', 'Analyzed At', 'Verdict'].map((h) => (
                <th key={h} className="px-4 py-3 font-medium">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr><td colSpan={8} className="px-4 py-8 text-center text-gray-500">Loading…</td></tr>
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
                <td className="px-4 py-3 font-mono" title={alert.mlModelVersion ?? 'no model'}>
                  {alert.mlScore == null ? (
                    <span className="text-gray-600">—</span>
                  ) : (
                    <span className={alert.mlScore >= 0.8 ? 'text-red-400' : alert.mlScore >= 0.5 ? 'text-yellow-400' : 'text-gray-300'}>
                      {(alert.mlScore * 100).toFixed(0)}%
                    </span>
                  )}
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
                <td className="px-4 py-3">
                  <div className="flex gap-1" title="Was this really fraud? Trains the ML model.">
                    {([true, false] as const).map((fraud) => {
                      const chosen = alert.labeledFraud === fraud
                      return (
                        <button
                          key={String(fraud)}
                          disabled={label.isPending}
                          onClick={() => label.mutate({ transactionId: alert.transactionId, fraud })}
                          className={`text-xs rounded px-2 py-1 disabled:opacity-40 ${
                            chosen
                              ? fraud ? 'bg-red-600 text-white' : 'bg-green-600 text-white'
                              : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
                          }`}
                        >
                          {fraud ? 'Fraud' : 'Legit'}
                        </button>
                      )
                    })}
                  </div>
                </td>
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
