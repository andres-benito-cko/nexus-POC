import { useState, useMemo, useCallback } from 'react'
import {
  ReactFlow,
  type Node,
  type Edge,
  Background,
  Controls,
  MarkerType,
  Position,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import type { NexusEngineConfigContent } from '../../pages/ConfigEditor'

interface StateMachineTabProps {
  content: NexusEngineConfigContent | null
}

// Custom node colors per state
const STATE_COLORS: Record<string, { bg: string; border: string; text: string }> = {
  LIVE: { bg: '#ecfdf5', border: '#34d399', text: '#065f46' },
  NOT_LIVE: { bg: '#fffbeb', border: '#fbbf24', text: '#92400e' },
}

const DEFAULT_COLOR = { bg: '#f0f9ff', border: '#60a5fa', text: '#1e40af' }

export default function StateMachineTab({ content }: StateMachineTabProps) {
  const families = useMemo(() => {
    if (!content?.state_machines) return []
    return Object.keys(content.state_machines)
  }, [content])

  const [selectedFamily, setSelectedFamily] = useState<string>(families[0] ?? '')

  // Update selection when families change
  if (families.length > 0 && !families.includes(selectedFamily)) {
    setSelectedFamily(families[0])
  }

  const machine = content?.state_machines?.[selectedFamily]

  const { nodes, edges } = useMemo(() => {
    if (!machine) return { nodes: [], edges: [] }

    const stateNames = Object.keys(machine.states)
    const nodeSpacingY = 140
    const centerX = 250

    // Build nodes — top-to-bottom layout
    const flowNodes: Node[] = stateNames.map((name, idx) => {
      const state = machine.states[name]
      const colorSet = STATE_COLORS[state.transaction_status] ?? DEFAULT_COLOR

      return {
        id: name,
        position: { x: centerX, y: idx * nodeSpacingY + 40 },
        data: {
          label: (
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4, color: colorSet.text }}>
                {name}
              </div>
              <div style={{ fontSize: 10, color: '#6b7280' }}>
                txn: {state.transaction_status}
              </div>
              <div style={{ fontSize: 10, color: '#6b7280' }}>
                trade: {state.trade_status}
              </div>
            </div>
          ),
        },
        style: {
          background: colorSet.bg,
          border: `2px solid ${colorSet.border}`,
          borderRadius: 12,
          padding: '10px 16px',
          minWidth: 160,
        },
        sourcePosition: Position.Bottom,
        targetPosition: Position.Top,
        draggable: false,
      }
    })

    // Build edges from transitions
    const flowEdges: Edge[] = []
    const transitions = machine.transitions ?? []

    // Find the default state (used as source for all conditional transitions)
    const defaultTransition = transitions.find((t) => 'default' in t)
    const defaultState = defaultTransition?.default

    transitions.forEach((t, idx) => {
      if ('default' in t && t.default) {
        // Default transition: draw from a virtual "start" or mark differently
        flowEdges.push({
          id: `default-${idx}`,
          source: '__start__',
          target: t.default,
          label: 'default',
          type: 'smoothstep',
          style: { stroke: '#9ca3af', strokeWidth: 2, strokeDasharray: '6 3' },
          labelStyle: { fontSize: 10, fill: '#9ca3af' },
          markerEnd: { type: MarkerType.ArrowClosed, color: '#9ca3af' },
        })
      } else if (t.to) {
        // Conditional transition: from default state (or first state) to target
        const sourceState = defaultState ?? stateNames[0]
        flowEdges.push({
          id: `transition-${idx}`,
          source: sourceState,
          target: t.to,
          label: t.when ?? '',
          type: 'smoothstep',
          style: { stroke: '#3b82f6', strokeWidth: 2 },
          labelStyle: { fontSize: 9, fill: '#6b7280', fontFamily: 'monospace' },
          markerEnd: { type: MarkerType.ArrowClosed, color: '#3b82f6' },
        })
      }
    })

    // Add a start node
    flowNodes.unshift({
      id: '__start__',
      position: { x: centerX + 50, y: 0 },
      data: { label: '' },
      style: {
        width: 20,
        height: 20,
        borderRadius: '50%',
        background: '#3b82f6',
        border: 'none',
        padding: 0,
      },
      sourcePosition: Position.Bottom,
      targetPosition: Position.Top,
      draggable: false,
    })

    return { nodes: flowNodes, edges: flowEdges }
  }, [machine])

  // Suppress React Flow warning about missing dimensions
  const onInit = useCallback(() => {}, [])

  if (!content) {
    return (
      <div className="flex items-center justify-center h-64 text-sm text-red-500">
        Unable to parse YAML content. Fix syntax errors in the Raw YAML tab.
      </div>
    )
  }

  if (families.length === 0) {
    return (
      <div className="flex items-center justify-center h-64 text-sm text-zinc-400">
        No state machines defined in the config.
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      {/* Family selector */}
      <div className="flex items-center gap-3 px-5 py-3 bg-white border-b border-zinc-100">
        <label className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">
          Trade Family
        </label>
        <div className="flex gap-1 bg-navy-900 rounded-lg p-1">
          {families.map((family) => (
            <button
              key={family}
              onClick={() => setSelectedFamily(family)}
              className={`px-3 py-1 rounded-md text-xs font-medium transition-all ${
                selectedFamily === family
                  ? 'text-zinc-900 bg-navy-600'
                  : 'text-zinc-500 hover:text-zinc-700'
              }`}
            >
              {family}
            </button>
          ))}
        </div>
        <span className="text-xs text-zinc-400 ml-auto">Read-only visual</span>
      </div>

      {/* React Flow canvas */}
      <div className="flex-1 min-h-[400px]">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onInit={onInit}
          fitView
          fitViewOptions={{ padding: 0.3 }}
          nodesDraggable={false}
          nodesConnectable={false}
          elementsSelectable={false}
          panOnDrag
          zoomOnScroll
          proOptions={{ hideAttribution: true }}
        >
          <Background gap={20} size={1} color="#e4e4e7" />
          <Controls showInteractive={false} />
        </ReactFlow>
      </div>
    </div>
  )
}
