const API = '/api'

// --- Types ---

export interface EngineConfig {
  id: string
  version: string
  content: string
  active: boolean
  validFrom: string | null
  validTo: string | null
  createdAt: string
  createdBy: string
}

export interface DlqEvent {
  id: string
  actionId: string
  payload: Record<string, unknown>
  errors: string[]
  createdAt: string
  replayedAt: string | null
}

export interface Transaction {
  transaction_id: string
  product_type: string
  transaction_type: string
  transaction_status: string
  transaction_amount: number
  transaction_currency: string
  legs?: unknown[]
}

export interface NexusBlock {
  nexus_id: string
  action_id: string
  action_root_id: string
  status: string
  processed_at: string
  cko_entity_id?: string
  entity?: { id: string }
  transactions?: Transaction[]
}

// --- Fetch helpers ---

async function fetchJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })
  if (!res.ok) {
    throw new Error(`API ${res.status}: ${res.statusText}`)
  }
  return res.json()
}

export function getConfigs(): Promise<EngineConfig[]> {
  return fetchJson('/configs')
}

export function getActiveConfig(): Promise<EngineConfig | null> {
  return fetchJson<EngineConfig>('/configs/active').catch(() => null)
}

export function activateConfig(id: string): Promise<void> {
  return fetchJson(`/configs/${id}/activate`, { method: 'POST' })
}

export function getDlq(): Promise<DlqEvent[]> {
  return fetchJson('/dlq')
}

export function replayDlq(id: string): Promise<void> {
  return fetchJson(`/dlq/${id}/replay`, { method: 'POST' })
}

export function getTransactions(): Promise<NexusBlock[]> {
  return fetchJson('/blocks')
}

export function createConfig(config: { version: string; content: string; createdBy: string }): Promise<EngineConfig> {
  return fetchJson('/configs', {
    method: 'POST',
    body: JSON.stringify(config),
  })
}

export function updateConfig(id: string, config: { version: string; content: string }): Promise<EngineConfig> {
  return fetchJson(`/configs/${id}`, {
    method: 'PUT',
    body: JSON.stringify(config),
  })
}

export interface ValidationResult {
  valid: boolean
  errors: string[]
}

export function validateConfig(content: string): Promise<ValidationResult> {
  return fetchJson('/configs/validate', {
    method: 'POST',
    body: JSON.stringify({ content }),
  })
}

// --- Test Bench ---

export interface TestBenchResult {
  success: boolean
  transaction?: NexusBlock & Record<string, unknown>
  errors?: string[]
}

export function runTestBench(leEvent: unknown): Promise<TestBenchResult> {
  return fetchJson('/test-bench', {
    method: 'POST',
    body: JSON.stringify(leEvent),
  })
}

// --- Simulator ---

export interface SimulatorScenario {
  id: string
  name: string
  description?: string
}

const SIMULATOR_API = ''

async function fetchSimulator<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${SIMULATOR_API}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })
  if (!res.ok) {
    throw new Error(`Simulator ${res.status}: ${res.statusText}`)
  }
  return res.json()
}

export function getScenarios(): Promise<SimulatorScenario[]> {
  return fetchSimulator('/simulate/scenarios')
}

export function playScenario(id: string): Promise<void> {
  return fetchSimulator(`/simulate/scenario/${id}`, { method: 'POST' })
}

export function stopSimulator(): Promise<void> {
  return fetchSimulator('/simulate/stop', { method: 'POST' })
}

// --- Rules Engine API (proxied via /rules-api) ---

const RULES_ENGINE_API = '/rules-api'

async function fetchRulesEngine<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${RULES_ENGINE_API}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!res.ok) throw new Error(`Rules Engine ${res.status}: ${res.statusText}`)
  return res.json()
}

// --- Account types and API ---

export interface Account {
  code: string
  name: string
  accountType: 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE' | 'CONTROL'
  normalBalance: 'DEBIT' | 'CREDIT'
  description?: string
  enabled: boolean
  createdAt: string
}

export function getAccounts(): Promise<Account[]> {
  return fetchRulesEngine('/accounts')
}

export function createAccount(account: Omit<Account, 'createdAt'>): Promise<Account> {
  return fetchRulesEngine('/accounts', { method: 'POST', body: JSON.stringify(account) })
}

export function updateAccount(code: string, account: Partial<Account>): Promise<Account> {
  return fetchRulesEngine(`/accounts/${code}`, { method: 'PUT', body: JSON.stringify(account) })
}

export function deleteAccount(code: string): Promise<void> {
  return fetchRulesEngine(`/accounts/${code}`, { method: 'DELETE' })
}

// --- Rule types and API ---

export interface Rule {
  id?: string
  name: string
  description?: string
  productType?: string
  transactionType?: string
  transactionStatus?: string
  legType?: string
  legStatus?: string
  firingContext: 'LEG' | 'FEE'
  feeType?: string
  passthrough?: boolean | null
  debitAccount: string
  creditAccount: string
  amountSource: string
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}

export function getRules(): Promise<Rule[]> {
  return fetchRulesEngine('/rules')
}

export function createRule(rule: Omit<Rule, 'id' | 'createdAt' | 'updatedAt'>): Promise<Rule> {
  return fetchRulesEngine('/rules', { method: 'POST', body: JSON.stringify(rule) })
}

export function updateRule(id: string, rule: Rule): Promise<Rule> {
  return fetchRulesEngine(`/rules/${id}`, { method: 'PUT', body: JSON.stringify(rule) })
}

export function deleteRule(id: string): Promise<void> {
  return fetchRulesEngine(`/rules/${id}`, { method: 'DELETE' })
}

// --- Posting error types and API ---

export interface PostingError {
  id: string
  nexusId: string
  transactionId: string
  currency: string
  debitTotal: number
  creditTotal: number
  ruleIds?: string
  createdAt: string
}

export function getPostingErrors(params?: { nexusId?: string; transactionId?: string }): Promise<PostingError[]> {
  const query = new URLSearchParams()
  if (params?.nexusId) query.set('nexusId', params.nexusId)
  if (params?.transactionId) query.set('transactionId', params.transactionId)
  return fetchRulesEngine(`/ledger/errors?${query}`)
}

// --- Ledger Entry types and API ---

export interface LedgerEntry {
  id: string
  ruleId: string | null
  ruleName: string | null
  nexusId: string
  transactionId: string
  legId: string | null
  account: string
  side: 'DEBIT' | 'CREDIT'
  amount: number
  currency: string
  productType: string | null
  transactionType: string | null
  transactionStatus: string | null
  createdAt: string
}

export function getLedgerEntries(params?: { nexusId?: string; limit?: number }): Promise<LedgerEntry[]> {
  const query = new URLSearchParams()
  if (params?.nexusId) query.set('nexusId', params.nexusId)
  if (params?.limit) query.set('limit', String(params.limit))
  return fetchRulesEngine(`/ledger/entries?${query}`)
}

// --- Block detail API ---

export interface NexusBlockRecord {
  nexusId: string
  actionId: string
  actionRootId: string | null
  status: string
  entityId: string | null
  ckoEntityId: string | null
  productType: string | null
  transactionType: string | null
  transactionStatus: string | null
  transactionAmount: number | null
  transactionCurrency: string | null
  rawJson: string
  receivedAt: string
  updatedAt: string
}

export function getBlock(nexusId: string): Promise<NexusBlockRecord> {
  return fetchRulesEngine(`/blocks/${nexusId}`)
}

export async function getBlockSource(nexusId: string): Promise<string> {
  const res = await fetch(`/rules-api/blocks/${nexusId}/source`)
  if (!res.ok) throw new Error(`Rules Engine ${res.status}: ${res.statusText}`)
  return res.text()
}

// --- AI Generator ---

export interface GenerateProgress {
  step: string
  message: string
}

export interface GenerateResult {
  success: boolean
  leTransaction?: Record<string, unknown>
  validationPassed?: boolean
  errors?: string[]
}

export async function generateLeTransaction(
  prompt: string,
  onProgress: (event: GenerateProgress) => void
): Promise<GenerateResult> {
  const res = await fetch('/api/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt }),
  })

  if (!res.ok) {
    throw new Error(`Generator ${res.status}: ${res.statusText}`)
  }

  const reader = res.body?.getReader()
  if (!reader) throw new Error('No response body')

  const decoder = new TextDecoder()
  let buffer = ''
  let finalResult: GenerateResult | null = null

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    let eventName = ''
    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (!data) continue
        try {
          const parsed = JSON.parse(data)
          if (eventName === 'progress') {
            onProgress(parsed as GenerateProgress)
          } else if (eventName === 'result') {
            finalResult = parsed as GenerateResult
          } else if (eventName === 'error') {
            finalResult = { success: false, errors: [parsed.message ?? 'Generation failed'] }
          }
        } catch {
          // Skip non-JSON lines
        }
      }
    }
  }

  if (finalResult) return finalResult
  throw new Error('No result received from generator')
}
