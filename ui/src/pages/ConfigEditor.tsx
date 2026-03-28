import { useState, useCallback, useMemo, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import yaml from 'js-yaml'
import {
  getConfigs,
  createConfig,
  updateConfig,
  activateConfig,
  validateConfig,
  type EngineConfig,
  type ValidationResult,
} from '../api/client'
import StatusBadge from '../components/StatusBadge'
import { showToast } from '../components/Toast'
import RawYamlTab from '../components/config/RawYamlTab'
import ClassificationTab from '../components/config/ClassificationTab'
import StateMachineTab from '../components/config/StateMachineTab'
import FieldMappingsTab from '../components/config/FieldMappingsTab'
import FeeTypeMappingsTab from '../components/config/FeeTypeMappingsTab'

// --- Types for parsed YAML ---

export interface ClassificationRule {
  when?: string
  default?: string
  result?: string
}

export interface ClassificationType {
  priority: string[]
  field_per_source: Record<string, string>
  mapping: Record<string, string[]>
}

export interface StateMachineState {
  block_status: string
  transaction_status: string
}

export interface StateMachineTransition {
  to?: string
  when?: string
  default?: string
}

export interface StateMachine {
  states: Record<string, StateMachineState>
  transitions: StateMachineTransition[]
}

export interface FieldMapping {
  $field: string[]
  $fallback?: string
}

export interface NexusEngineConfigContent {
  version?: string
  classification?: {
    family?: ClassificationRule[]
    type?: ClassificationType
  }
  state_machines?: Record<string, StateMachine>
  transactions?: Record<string, Record<string, unknown>>
  field_mappings?: Record<string, FieldMapping>
  fee_type_mappings?: Record<string, Record<string, string>>
}

// --- Tabs ---

const TABS = [
  { id: 'yaml', label: 'Raw YAML' },
  { id: 'classification', label: 'Classification' },
  { id: 'state-machines', label: 'State Machines' },
  { id: 'field-mappings', label: 'Field Mappings' },
  { id: 'fee-types', label: 'Fee Types' },
] as const

type TabId = (typeof TABS)[number]['id']

// --- Helpers ---

function formatDate(iso: string | null): string {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return iso
  }
}

function parseYamlContent(yamlStr: string): NexusEngineConfigContent | null {
  try {
    return yaml.load(yamlStr) as NexusEngineConfigContent
  } catch {
    return null
  }
}

function serializeToYaml(content: NexusEngineConfigContent): string {
  return yaml.dump(content, { lineWidth: 120, noRefs: true, sortKeys: false })
}

// --- Main Component ---

export default function ConfigEditor() {
  const queryClient = useQueryClient()

  // Config list
  const { data: configs = [] } = useQuery({
    queryKey: ['configs'],
    queryFn: getConfigs,
  })

  // Selection state
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [isNew, setIsNew] = useState(false)
  const [activeTab, setActiveTab] = useState<TabId>('yaml')

  // Editor state
  const [yamlContent, setYamlContent] = useState('')
  const [version, setVersion] = useState('')
  const [validationResult, setValidationResult] = useState<ValidationResult | null>(null)

  // Parsed content (derived from yamlContent)
  const parsedContent = useMemo(() => parseYamlContent(yamlContent), [yamlContent])

  // Load selected config
  const selectedConfig = useMemo(
    () => configs.find((c) => c.id === selectedId) ?? null,
    [configs, selectedId]
  )

  useEffect(() => {
    if (selectedConfig) {
      setYamlContent(selectedConfig.content)
      setVersion(selectedConfig.version)
      setValidationResult(null)
    }
  }, [selectedConfig])

  // --- Mutations ---

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (isNew || !selectedId) {
        return createConfig({ version, content: yamlContent, createdBy: 'ui-editor' })
      }
      return updateConfig(selectedId, { version, content: yamlContent })
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['configs'] })
      setSelectedId(saved.id)
      setIsNew(false)
      showToast('Config saved successfully', 'success')
    },
    onError: () => {
      showToast('Failed to save config', 'error')
    },
  })

  const activateMutation = useMutation({
    mutationFn: activateConfig,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['configs'] })
      queryClient.invalidateQueries({ queryKey: ['config', 'active'] })
      showToast('Config activated', 'success')
    },
    onError: () => {
      showToast('Failed to activate config', 'error')
    },
  })

  const validateMutation = useMutation({
    mutationFn: async () => {
      return validateConfig(yamlContent)
    },
    onSuccess: (result) => {
      setValidationResult(result)
      if (result.valid) {
        showToast('Config is valid', 'success')
      }
    },
    onError: () => {
      setValidationResult({ valid: false, errors: ['Failed to reach validation endpoint'] })
    },
  })

  // --- Handlers ---

  const handleNewConfig = useCallback(() => {
    setSelectedId(null)
    setIsNew(true)
    setVersion('1.0-draft')
    setYamlContent('version: "1.0"\n\nclassification:\n  family:\n    - default: ACQUIRING\n  type:\n    priority: []\n    field_per_source: {}\n    mapping: {}\n\nstate_machines: {}\n\nfield_mappings: {}\n\nfee_type_mappings: {}\n')
    setValidationResult(null)
    setActiveTab('yaml')
  }, [])

  const handleSelectConfig = useCallback((id: string) => {
    setSelectedId(id)
    setIsNew(false)
    setValidationResult(null)
  }, [])

  // Update YAML from structured edits
  const updateFromStructured = useCallback(
    (updater: (content: NexusEngineConfigContent) => NexusEngineConfigContent) => {
      const current = parseYamlContent(yamlContent)
      if (!current) return
      const updated = updater(current)
      setYamlContent(serializeToYaml(updated))
    },
    [yamlContent]
  )

  const hasSelection = selectedId !== null || isNew

  return (
    <div className="flex h-[calc(100vh-3.5rem-3rem)] -m-6 fade-in">
      {/* Left sidebar — config list */}
      <aside className="w-72 flex-shrink-0 border-r border-zinc-200 bg-white overflow-y-auto">
        <div className="p-4 border-b border-zinc-100">
          <button
            onClick={handleNewConfig}
            className="w-full px-4 py-2 rounded-lg bg-accent text-white text-sm font-medium hover:bg-accent-dark transition-colors"
          >
            + New Config
          </button>
        </div>
        <div className="divide-y divide-zinc-50">
          {configs.map((c: EngineConfig) => (
            <button
              key={c.id}
              onClick={() => handleSelectConfig(c.id)}
              className={`w-full text-left px-4 py-3 transition-colors hover:bg-accent-glow ${
                selectedId === c.id ? 'bg-blue-50 border-l-2 border-blue-500' : ''
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-zinc-900">{c.version}</span>
                {c.active && <StatusBadge status="ACTIVE" />}
              </div>
              <p className="text-xs text-zinc-400 mt-1">{formatDate(c.createdAt)}</p>
              <div className="flex items-center gap-2 mt-2">
                {!c.active && (
                  <button
                    onClick={(e) => {
                      e.stopPropagation()
                      activateMutation.mutate(c.id)
                    }}
                    className="px-2 py-0.5 rounded text-[10px] font-medium bg-emerald-50 text-emerald-700 hover:bg-emerald-100 transition-colors"
                  >
                    Activate
                  </button>
                )}
              </div>
            </button>
          ))}
          {configs.length === 0 && (
            <div className="px-4 py-8 text-center text-sm text-zinc-400">
              No configs yet
            </div>
          )}
        </div>
      </aside>

      {/* Right — editor area */}
      <div className="flex-1 flex flex-col overflow-hidden bg-navy-800">
        {!hasSelection ? (
          <div className="flex-1 flex items-center justify-center">
            <div className="text-center">
              <svg
                className="w-12 h-12 mx-auto mb-3 text-zinc-300"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
                />
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
                />
              </svg>
              <p className="text-sm font-medium text-zinc-500">Select a config or create a new one</p>
              <p className="text-xs text-zinc-400 mt-1">
                Use the sidebar to browse existing configurations
              </p>
            </div>
          </div>
        ) : (
          <>
            {/* Top bar — version + actions */}
            <div className="flex items-center justify-between px-5 py-3 border-b border-zinc-200 bg-white">
              <div className="flex items-center gap-3">
                <label className="text-xs font-medium text-zinc-500 uppercase tracking-wider">
                  Version
                </label>
                <input
                  type="text"
                  value={version}
                  onChange={(e) => setVersion(e.target.value)}
                  className="px-3 py-1.5 rounded-md border border-zinc-200 text-sm font-medium text-zinc-900 bg-navy-800 focus:outline-none focus:ring-2 focus:ring-blue-300 w-40"
                />
                {isNew && (
                  <span className="inline-flex items-center rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-semibold text-amber-600 uppercase">
                    New
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => validateMutation.mutate()}
                  disabled={validateMutation.isPending}
                  className="px-4 py-1.5 rounded-md border border-zinc-200 text-sm font-medium text-zinc-700 hover:bg-zinc-50 transition-colors disabled:opacity-50"
                >
                  {validateMutation.isPending ? 'Validating...' : 'Validate'}
                </button>
                <button
                  onClick={() => saveMutation.mutate()}
                  disabled={saveMutation.isPending}
                  className="px-4 py-1.5 rounded-md bg-accent text-white text-sm font-medium hover:bg-accent-dark transition-colors disabled:opacity-50"
                >
                  {saveMutation.isPending ? 'Saving...' : 'Save'}
                </button>
              </div>
            </div>

            {/* Validation banner */}
            {validationResult && (
              <div
                className={`px-5 py-2 text-sm font-medium ${
                  validationResult.valid
                    ? 'bg-emerald-50 text-emerald-700 border-b border-emerald-200'
                    : 'bg-red-50 text-red-700 border-b border-red-200'
                }`}
              >
                {validationResult.valid ? (
                  'Config is valid'
                ) : (
                  <div>
                    <p className="font-semibold">Validation errors:</p>
                    <ul className="mt-1 space-y-0.5">
                      {validationResult.errors.map((err, i) => (
                        <li key={i} className="text-xs font-mono">
                          {err}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}

            {/* Tab bar */}
            <div className="flex border-b border-zinc-200 bg-white px-5">
              {TABS.map((tab) => (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={`px-4 py-2.5 text-sm font-medium transition-colors border-b-2 -mb-px ${
                    activeTab === tab.id
                      ? 'text-blue-600 border-blue-500'
                      : 'text-zinc-500 border-transparent hover:text-zinc-700 hover:border-zinc-300'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {/* Tab content */}
            <div className="flex-1 overflow-auto">
              {activeTab === 'yaml' && (
                <RawYamlTab
                  value={yamlContent}
                  onChange={setYamlContent}
                />
              )}
              {activeTab === 'classification' && (
                <ClassificationTab
                  content={parsedContent}
                  onUpdate={updateFromStructured}
                />
              )}
              {activeTab === 'state-machines' && (
                <StateMachineTab content={parsedContent} />
              )}
              {activeTab === 'field-mappings' && (
                <FieldMappingsTab
                  content={parsedContent}
                  onUpdate={updateFromStructured}
                />
              )}
              {activeTab === 'fee-types' && (
                <FeeTypeMappingsTab
                  content={parsedContent}
                  onUpdate={updateFromStructured}
                />
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
