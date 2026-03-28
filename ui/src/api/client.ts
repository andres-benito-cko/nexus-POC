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

export interface NexusTransaction {
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
  transactions?: NexusTransaction[]
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
