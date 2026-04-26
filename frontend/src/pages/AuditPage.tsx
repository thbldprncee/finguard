import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import Layout from '../components/Layout'
import apiClient from '../api/axiosClient'
import type { AuditEvent, PageResponse } from '../types'

export default function AuditPage() {
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['audit', page],
    queryFn: () =>
      apiClient.get<PageResponse<AuditEvent>>('/audit/events', {
        params: { page, size: 50, sort: 'createdAt,desc' },
      }).then((r) => r.data),
  })

  return (
    <Layout>
      <h1 className="text-2xl font-bold text-white mb-6">Audit Log</h1>

      <div className="bg-gray-900 rounded-xl border border-gray-800 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-800 text-gray-400 text-left">
              {['Event Type', 'Aggregate ID', 'Correlation ID', 'Timestamp'].map((h) => (
                <th key={h} className="px-4 py-3 font-medium">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-500">Loading…</td></tr>
            )}
            {(data?.content ?? []).map((event) => (
              <tr key={event.id} className="border-b border-gray-800/50 hover:bg-gray-800/40">
                <td className="px-4 py-3">
                  <span className="bg-blue-900/40 text-blue-300 text-xs rounded px-2 py-0.5 font-mono">
                    {event.eventType}
                  </span>
                </td>
                <td className="px-4 py-3 font-mono text-gray-300">
                  {event.aggregateId ? event.aggregateId.slice(0, 16) + '…' : '—'}
                </td>
                <td className="px-4 py-3 font-mono text-gray-500 text-xs">
                  {event.correlationId ? event.correlationId.slice(0, 12) + '…' : '—'}
                </td>
                <td className="px-4 py-3 text-gray-500">{new Date(event.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-800 text-sm">
          <span className="text-gray-400">{data?.totalElements ?? 0} events</span>
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
