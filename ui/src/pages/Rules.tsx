import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getRules, createRule, updateRule, deleteRule, getAccounts, type Rule, type Account } from '../api/client'
import { showToast } from '../components/Toast'

const EMPTY_FORM: Omit<Rule, 'id' | 'createdAt' | 'updatedAt'> = {
  name: '', description: '', productType: '', transactionType: '', transactionStatus: '',
  legType: '', legStatus: '', firingContext: 'LEG', feeType: '', passthrough: null,
  debitAccount: '', creditAccount: '', amountSource: 'leg_amount', enabled: true,
}

export default function Rules() {
  const qc = useQueryClient()
  const { data: rules = [], isLoading } = useQuery({ queryKey: ['rules'], queryFn: getRules })
  const { data: accounts = [] } = useQuery({ queryKey: ['accounts'], queryFn: getAccounts })
  const enabledAccounts = accounts.filter((a: Account) => a.enabled)

  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Rule | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)

  const createMut = useMutation({
    mutationFn: createRule,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['rules'] }); closeForm(); showToast('Rule created', 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const updateMut = useMutation({
    mutationFn: ({ id, rule }: { id: string; rule: Rule }) => updateRule(id, rule),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['rules'] }); closeForm(); showToast('Rule updated', 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const deleteMut = useMutation({
    mutationFn: deleteRule,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['rules'] }); showToast('Rule deleted', 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  function openCreate() { setEditing(null); setForm(EMPTY_FORM); setShowForm(true) }
  function openEdit(r: Rule) { setEditing(r); setForm({ ...r }); setShowForm(true) }
  function closeForm() { setShowForm(false); setEditing(null) }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const payload = {
      ...form,
      productType: form.productType || undefined,
      transactionType: form.transactionType || undefined,
      transactionStatus: form.transactionStatus || undefined,
      legType: form.legType || undefined,
      legStatus: form.legStatus || undefined,
      feeType: form.firingContext === 'FEE' ? (form.feeType || undefined) : undefined,
      passthrough: form.firingContext === 'FEE' ? form.passthrough : null,
      amountSource: form.firingContext === 'FEE' ? 'fee_amount' : form.amountSource,
    }
    if (editing?.id) {
      updateMut.mutate({ id: editing.id, rule: { ...payload, id: editing.id } })
    } else {
      createMut.mutate(payload)
    }
  }

  function field(label: string, key: keyof typeof form, type: 'text' | 'select' = 'text', options?: string[]) {
    if (type === 'select' && options) {
      return (
        <div>
          <label className="block text-xs text-zinc-500 mb-1">{label}</label>
          <select className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
            value={(form[key] as string) ?? ''}
            onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))}>
            <option value="">— any —</option>
            {options.map(o => <option key={o}>{o}</option>)}
          </select>
        </div>
      )
    }
    return (
      <div>
        <label className="block text-xs text-zinc-500 mb-1">{label}</label>
        <input className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
          value={(form[key] as string) ?? ''}
          onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))} />
      </div>
    )
  }

  function accountSelect(label: string, key: 'debitAccount' | 'creditAccount', required = true) {
    return (
      <div>
        <label className="block text-xs text-zinc-500 mb-1">{label} {required && '*'}</label>
        <select className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
          value={form[key]}
          required={required}
          onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))}>
          <option value="">— select account —</option>
          {enabledAccounts.map((a: Account) => (
            <option key={a.code} value={a.code}>{a.code} — {a.name}</option>
          ))}
        </select>
      </div>
    )
  }

  return (
    <div className="max-w-7xl mx-auto space-y-6 fade-in">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-zinc-900">Rules</h1>
        <button onClick={openCreate}
          className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 transition-colors">
          + New Rule
        </button>
      </div>

      {showForm && (
        <div className="glow-border rounded-xl p-5">
          <h2 className="text-sm font-semibold text-zinc-700 mb-4">
            {editing ? 'Edit Rule' : 'New Rule'}
          </h2>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-3 gap-4">
              <div className="col-span-2">
                <label className="block text-xs text-zinc-500 mb-1">Name *</label>
                <input className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                  value={form.name} required
                  onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
              </div>
              <div>
                <label className="block text-xs text-zinc-500 mb-1">Firing Context *</label>
                <select className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                  value={form.firingContext}
                  onChange={e => setForm(f => ({
                    ...f,
                    firingContext: e.target.value as 'LEG' | 'FEE',
                    amountSource: e.target.value === 'FEE' ? 'fee_amount' : 'leg_amount',
                    feeType: '',
                    passthrough: null,
                  }))}>
                  <option value="LEG">LEG</option>
                  <option value="FEE">FEE</option>
                </select>
              </div>
            </div>
            <div className="grid grid-cols-3 gap-4">
              {field('Product Type', 'productType')}
              {field('Transaction Type', 'transactionType')}
              {field('Transaction Status', 'transactionStatus')}
              {field('Leg Type', 'legType')}
              {field('Leg Status', 'legStatus')}
              {form.firingContext === 'FEE' && field('Fee Type *', 'feeType')}
              {form.firingContext === 'FEE' && (
                <div>
                  <label className="block text-xs text-zinc-500 mb-1">Passthrough</label>
                  <select className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                    value={form.passthrough === null ? '' : String(form.passthrough)}
                    onChange={e => setForm(f => ({
                      ...f,
                      passthrough: e.target.value === '' ? null : e.target.value === 'true',
                    }))}>
                    <option value="">— any —</option>
                    <option value="true">true</option>
                    <option value="false">false</option>
                  </select>
                </div>
              )}
            </div>
            <div className="grid grid-cols-2 gap-4">
              {accountSelect('Debit Account', 'debitAccount')}
              {accountSelect('Credit Account', 'creditAccount')}
            </div>
            <div className="flex items-center gap-2">
              <input type="checkbox" id="rule-enabled" checked={form.enabled}
                onChange={e => setForm(f => ({ ...f, enabled: e.target.checked }))} />
              <label htmlFor="rule-enabled" className="text-sm text-zinc-700">Enabled</label>
            </div>
            <div className="flex gap-2 justify-end">
              <button type="button" onClick={closeForm}
                className="px-4 py-2 rounded-md border border-zinc-200 text-sm hover:bg-zinc-50">Cancel</button>
              <button type="submit"
                className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700">
                {editing ? 'Save' : 'Create'}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="glow-border rounded-xl p-5 overflow-x-auto">
        {isLoading ? <p className="text-sm text-zinc-400">Loading…</p> : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-zinc-100">
                {['Name', 'Context', 'Product', 'Txn Type', 'Leg Type', 'Leg Status', 'Fee Type', 'Debit', 'Credit', 'Enabled', ''].map(h => (
                  <th key={h} className="pb-2 pr-3 text-xs font-semibold text-zinc-400 uppercase tracking-wider text-left whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rules.map((r: Rule) => (
                <tr key={r.id} className="border-b border-zinc-50 hover:bg-zinc-50 transition-colors">
                  <td className="py-2 pr-3 font-medium text-zinc-900 whitespace-nowrap">{r.name}</td>
                  <td className="py-2 pr-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-mono ${r.firingContext === 'FEE' ? 'bg-purple-50 text-purple-700' : 'bg-blue-50 text-blue-700'}`}>
                      {r.firingContext}
                    </span>
                  </td>
                  <td className="py-2 pr-3 text-zinc-500 text-xs">{r.productType ?? '—'}</td>
                  <td className="py-2 pr-3 text-zinc-500 text-xs">{r.transactionType ?? '—'}</td>
                  <td className="py-2 pr-3 text-zinc-500 text-xs">{r.legType ?? '—'}</td>
                  <td className="py-2 pr-3 text-zinc-500 text-xs">{r.legStatus ?? '—'}</td>
                  <td className="py-2 pr-3 text-zinc-500 text-xs">{r.feeType ?? '—'}</td>
                  <td className="py-2 pr-3 font-mono text-xs text-zinc-600">{r.debitAccount}</td>
                  <td className="py-2 pr-3 font-mono text-xs text-zinc-600">{r.creditAccount}</td>
                  <td className="py-2 pr-3">
                    <span className={`px-2 py-0.5 rounded-full text-xs ${r.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600'}`}>
                      {r.enabled ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td className="py-2 flex gap-2 whitespace-nowrap">
                    <button onClick={() => openEdit(r)} className="text-xs text-blue-600 hover:underline">Edit</button>
                    <button onClick={() => r.id && deleteMut.mutate(r.id)} className="text-xs text-red-500 hover:underline">Delete</button>
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
