import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getAccounts, createAccount, updateAccount, deleteAccount,
  type Account,
} from '../api/client'
import { showToast } from '../components/Toast'

const ACCOUNT_TYPES = ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE', 'CONTROL'] as const
const NORMAL_BALANCES = ['DEBIT', 'CREDIT'] as const

const EMPTY_FORM: Omit<Account, 'createdAt'> = {
  code: '', name: '', accountType: 'ASSET', normalBalance: 'DEBIT',
  description: '', enabled: true,
}

export default function ChartOfAccounts() {
  const qc = useQueryClient()
  const { data: accounts = [], isLoading } = useQuery({ queryKey: ['accounts'], queryFn: getAccounts })

  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Account | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)

  const createMut = useMutation({
    mutationFn: createAccount,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['accounts'] }); closeForm(); showToast('Account created', 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const updateMut = useMutation({
    mutationFn: ({ code, data }: { code: string; data: Partial<Account> }) => updateAccount(code, data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['accounts'] }); closeForm(); showToast('Account updated', 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const deleteMut = useMutation({
    mutationFn: deleteAccount,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['accounts'] }); showToast('Account disabled', 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  function openCreate() { setEditing(null); setForm(EMPTY_FORM); setShowForm(true) }
  function openEdit(a: Account) { setEditing(a); setForm({ ...a }); setShowForm(true) }
  function closeForm() { setShowForm(false); setEditing(null) }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (editing) {
      updateMut.mutate({ code: editing.code, data: form })
    } else {
      createMut.mutate(form)
    }
  }

  return (
    <div className="max-w-6xl mx-auto space-y-6 fade-in">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-zinc-900">Chart of Accounts</h1>
        <button onClick={openCreate}
          className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 transition-colors">
          + New Account
        </button>
      </div>

      {showForm && (
        <div className="glow-border rounded-xl p-5">
          <h2 className="text-sm font-semibold text-zinc-700 mb-4">
            {editing ? `Edit: ${editing.code}` : 'New Account'}
          </h2>
          <form onSubmit={handleSubmit} className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs text-zinc-500 mb-1">Code *</label>
              <input
                className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                value={form.code}
                onChange={e => setForm(f => ({ ...f, code: e.target.value }))}
                disabled={!!editing}
                required
              />
            </div>
            <div>
              <label className="block text-xs text-zinc-500 mb-1">Name *</label>
              <input
                className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                value={form.name}
                onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                required
              />
            </div>
            <div>
              <label className="block text-xs text-zinc-500 mb-1">Type *</label>
              <select
                className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                value={form.accountType}
                onChange={e => setForm(f => ({ ...f, accountType: e.target.value as Account['accountType'] }))}>
                {ACCOUNT_TYPES.map(t => <option key={t}>{t}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs text-zinc-500 mb-1">Normal Balance *</label>
              <select
                className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                value={form.normalBalance}
                onChange={e => setForm(f => ({ ...f, normalBalance: e.target.value as Account['normalBalance'] }))}>
                {NORMAL_BALANCES.map(b => <option key={b}>{b}</option>)}
              </select>
            </div>
            <div className="col-span-2">
              <label className="block text-xs text-zinc-500 mb-1">Description</label>
              <textarea
                className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                rows={2}
                value={form.description ?? ''}
                onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
              />
            </div>
            <div className="flex items-center gap-2">
              <input type="checkbox" id="enabled" checked={form.enabled}
                onChange={e => setForm(f => ({ ...f, enabled: e.target.checked }))} />
              <label htmlFor="enabled" className="text-sm text-zinc-700">Enabled</label>
            </div>
            <div className="col-span-2 flex gap-2 justify-end">
              <button type="button" onClick={closeForm}
                className="px-4 py-2 rounded-md border border-zinc-200 text-sm hover:bg-zinc-50">
                Cancel
              </button>
              <button type="submit"
                className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700">
                {editing ? 'Save' : 'Create'}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="glow-border rounded-xl p-5">
        {isLoading ? (
          <p className="text-sm text-zinc-400">Loading…</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-zinc-100">
                {['Code', 'Name', 'Type', 'Normal Balance', 'Enabled', ''].map(h => (
                  <th key={h} className="pb-2 pr-4 text-xs font-semibold text-zinc-400 uppercase tracking-wider text-left">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {accounts.map(a => (
                <tr key={a.code} className="border-b border-zinc-50 hover:bg-zinc-50 transition-colors">
                  <td className="py-2.5 pr-4 font-mono text-xs text-zinc-700">{a.code}</td>
                  <td className="py-2.5 pr-4 text-zinc-700">{a.name}</td>
                  <td className="py-2.5 pr-4 text-zinc-500">{a.accountType}</td>
                  <td className="py-2.5 pr-4 text-zinc-500">{a.normalBalance}</td>
                  <td className="py-2.5 pr-4">
                    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${a.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600'}`}>
                      {a.enabled ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td className="py-2.5 flex gap-2">
                    <button onClick={() => openEdit(a)}
                      className="text-xs text-blue-600 hover:underline">Edit</button>
                    {a.enabled && (
                      <button onClick={() => deleteMut.mutate(a.code)}
                        className="text-xs text-red-500 hover:underline">Disable</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
