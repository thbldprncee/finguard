import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import Layout from '../components/Layout'
import RiskBadge from '../components/RiskBadge'
import { transactionApi } from '../api/transactionApi'
import { accountApi } from '../api/accountApi'
import { errorMessage } from '../api/axiosClient'
import type { CreateTransactionRequest } from '../types'
import { Plus, X } from 'lucide-react'

const CURRENCIES = ['USD', 'EUR', 'GBP', 'CHF']
const COUNTRIES  = ['US', 'DE', 'GB', 'FR', 'CN', 'RU', 'UA', 'BR']
const METHODS    = ['CARD', 'BANK_TRANSFER', 'CRYPTO', 'PAYPAL']
const CATEGORIES = ['RETAIL', 'FOOD', 'TRAVEL', 'GAMBLING', 'CRYPTO', 'ADULT', 'OTHER']

const emptyForm = (sourceAccount = '', currency = 'USD'): CreateTransactionRequest => ({
  sourceAccount, targetAccount: '', amount: 100, currency,
  country: 'US', merchantCategory: 'RETAIL', paymentMethod: 'CARD',
})

export default function TransactionsPage() {
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState<CreateTransactionRequest>(emptyForm())
  // One key per form submission attempt: a retry or double click of the same form
  // reuses it, so the server creates the transaction at most once.
  const [idempotencyKey, setIdempotencyKey] = useState(crypto.randomUUID())
  const [newAccountCurrency, setNewAccountCurrency] = useState('USD')
  const [page, setPage] = useState(0)
  const qc = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['transactions', page],
    queryFn: () => transactionApi.list(page, 20),
    // The fraud decision arrives asynchronously; poll while anything on the page is still PENDING.
    refetchInterval: (query) =>
      query.state.data?.content.some((tx) => tx.status === 'PENDING') ? 2000 : false,
  })

  const { data: accounts = [] } = useQuery({
    queryKey: ['accounts'],
    queryFn: accountApi.list,
  })

  const openAccount = useMutation({
    mutationFn: (currency: string) => accountApi.open(currency),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['accounts'] }),
  })

  const createMutation = useMutation({
    mutationFn: (req: CreateTransactionRequest) => transactionApi.create(req, idempotencyKey),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['transactions'] })
      setShowForm(false)
    },
  })

  const startForm = () => {
    const first = accounts[0]
    setForm(emptyForm(first?.accountNumber ?? '', first?.currency ?? 'USD'))
    setIdempotencyKey(crypto.randomUUID())
    createMutation.reset()
    setShowForm(true)
  }

  const set = (k: keyof CreateTransactionRequest, v: unknown) =>
    setForm((f) => ({ ...f, [k]: v }))

  const selectSource = (accountNumber: string) => {
    const account = accounts.find((a) => a.accountNumber === accountNumber)
    setForm((f) => ({ ...f, sourceAccount: accountNumber, currency: account?.currency ?? f.currency }))
  }

  const inputClass = 'w-full bg-gray-800 border border-gray-700 rounded px-3 py-2 text-sm text-white'

  return (
    <Layout>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-white">Transactions</h1>
        <div className="flex items-center gap-2">
          <select value={newAccountCurrency} onChange={(e) => setNewAccountCurrency(e.target.value)}
            className="bg-gray-800 border border-gray-700 rounded-lg px-2 py-2 text-sm text-white">
            {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
          <button
            onClick={() => openAccount.mutate(newAccountCurrency)}
            disabled={openAccount.isPending}
            className="bg-gray-700 hover:bg-gray-600 disabled:opacity-50 text-white px-4 py-2 rounded-lg text-sm"
          >
            Open account
          </button>
          <button
            onClick={startForm}
            disabled={accounts.length === 0}
            title={accounts.length === 0 ? 'Open an account first' : undefined}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white px-4 py-2 rounded-lg text-sm"
          >
            <Plus size={16} /> New Transaction
          </button>
        </div>
      </div>

      {openAccount.isError && (
        <p className="mb-4 text-red-400 text-sm">{errorMessage(openAccount.error, 'Could not open account')}</p>
      )}

      {accounts.length > 0 && (
        <div className="mb-6 flex flex-wrap gap-2">
          {accounts.map((a) => (
            <span key={a.id} className="text-xs bg-gray-900 border border-gray-800 rounded px-3 py-1.5 text-gray-300 font-mono">
              {a.accountNumber} · {a.currency}
            </span>
          ))}
        </div>
      )}

      {showForm && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="bg-gray-900 rounded-2xl p-6 w-full max-w-lg border border-gray-700">
            <div className="flex justify-between items-center mb-5">
              <h2 className="text-lg font-semibold text-white">Create Transaction</h2>
              <button onClick={() => setShowForm(false)}><X className="text-gray-400" /></button>
            </div>
            <form
              onSubmit={(e) => { e.preventDefault(); createMutation.mutate(form) }}
              className="grid grid-cols-2 gap-3"
            >
              <div>
                <label className="text-xs text-gray-400 block mb-1">From account</label>
                <select value={form.sourceAccount} onChange={(e) => selectSource(e.target.value)} className={inputClass}>
                  {accounts.map((a) => <option key={a.id} value={a.accountNumber}>{a.accountNumber} ({a.currency})</option>)}
                </select>
              </div>
              <div>
                <label className="text-xs text-gray-400 block mb-1">Target account</label>
                <input value={form.targetAccount} required maxLength={34}
                  onChange={(e) => set('targetAccount', e.target.value.toUpperCase())} className={inputClass} />
              </div>
              <div>
                <label className="text-xs text-gray-400 block mb-1">Amount ({form.currency})</label>
                <input type="number" min="0.01" step="0.01" value={form.amount}
                  onChange={(e) => set('amount', parseFloat(e.target.value))} className={inputClass} />
              </div>
              {[
                ['country',  'Country',  COUNTRIES],
                ['paymentMethod', 'Payment Method', METHODS],
                ['merchantCategory', 'Merchant Category', CATEGORIES],
              ].map(([key, label, opts]) => (
                <div key={key as string}>
                  <label className="text-xs text-gray-400 block mb-1">{label as string}</label>
                  <select value={String(form[key as keyof CreateTransactionRequest] ?? '')}
                    onChange={(e) => set(key as keyof CreateTransactionRequest, e.target.value)} className={inputClass}>
                    {(opts as string[]).map((o) => <option key={o} value={o}>{o}</option>)}
                  </select>
                </div>
              ))}
              {createMutation.isError && (
                <p className="col-span-2 text-red-400 text-sm">
                  {errorMessage(createMutation.error, 'Transaction could not be created')}
                </p>
              )}
              <div className="col-span-2 flex gap-3 mt-2">
                <button type="button" onClick={() => setShowForm(false)}
                  className="flex-1 bg-gray-700 text-white py-2 rounded-lg text-sm">Cancel</button>
                <button type="submit" disabled={createMutation.isPending}
                  className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white py-2 rounded-lg text-sm">
                  {createMutation.isPending ? 'Submitting…' : 'Submit'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <div className="bg-gray-900 rounded-xl border border-gray-800 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-800 text-gray-400 text-left">
              {['ID', 'Amount', 'Country', 'Method', 'Status', 'Created'].map((h) => (
                <th key={h} className="px-4 py-3 font-medium">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-500">Loading…</td></tr>
            )}
            {(data?.content ?? []).map((tx) => (
              <tr key={tx.id} className="border-b border-gray-800/50 hover:bg-gray-800/40">
                <td className="px-4 py-3 font-mono text-gray-300">{tx.id.slice(0, 12)}…</td>
                <td className="px-4 py-3 text-white">{tx.amount} {tx.currency}</td>
                <td className="px-4 py-3 text-gray-300">{tx.country}</td>
                <td className="px-4 py-3 text-gray-300">{tx.paymentMethod}</td>
                <td className="px-4 py-3"><RiskBadge decision={tx.status} /></td>
                <td className="px-4 py-3 text-gray-500">{new Date(tx.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-800 text-sm">
          <span className="text-gray-400">{data?.totalElements ?? 0} total</span>
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
