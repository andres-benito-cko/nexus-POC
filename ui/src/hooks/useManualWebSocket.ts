import { useCallback, useRef, useState } from 'react'

const WS_URL = `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`
const MAX_MESSAGES = 200

export interface WsMessage {
  type: string
  payload: unknown
  receivedAt: string
}

export interface ManualWebSocketState {
  connected: boolean
  messages: WsMessage[]
  connect: () => void
  disconnect: () => void
  clearMessages: () => void
}

/**
 * WebSocket hook with manual connect/disconnect control.
 * Unlike useWebSocket, this does NOT auto-connect or auto-reconnect.
 */
export function useManualWebSocket(): ManualWebSocketState {
  const [connected, setConnected] = useState(false)
  const [messages, setMessages] = useState<WsMessage[]>([])
  const wsRef = useRef<WebSocket | null>(null)

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return

    const ws = new WebSocket(WS_URL)
    wsRef.current = ws

    ws.onopen = () => {
      setConnected(true)
    }

    ws.onmessage = (event) => {
      try {
        const raw = JSON.parse(event.data as string) as { type?: string; payload?: unknown }
        const msg: WsMessage = {
          type: raw.type ?? 'UNKNOWN',
          payload: raw.payload ?? raw,
          receivedAt: new Date().toISOString(),
        }
        setMessages((prev) => {
          const next = [msg, ...prev]
          return next.length > MAX_MESSAGES ? next.slice(0, MAX_MESSAGES) : next
        })
      } catch {
        // ignore non-JSON messages
      }
    }

    ws.onclose = () => {
      setConnected(false)
    }

    ws.onerror = () => {
      ws.close()
    }
  }, [])

  const disconnect = useCallback(() => {
    wsRef.current?.close()
    wsRef.current = null
  }, [])

  const clearMessages = useCallback(() => {
    setMessages([])
  }, [])

  return { connected, messages, connect, disconnect, clearMessages }
}
