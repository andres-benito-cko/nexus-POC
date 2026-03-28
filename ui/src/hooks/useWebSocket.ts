import { useEffect, useRef, useState, useCallback } from 'react'

const WS_URL = `ws://${window.location.host}/ws`
const RECONNECT_DELAY = 3_000
const MAX_MESSAGES = 100

export interface WebSocketState {
  connected: boolean
  messages: unknown[]
  send: (data: string) => void
}

export function useWebSocket(): WebSocketState {
  const [connected, setConnected] = useState(false)
  const [messages, setMessages] = useState<unknown[]>([])
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimer = useRef<ReturnType<typeof setTimeout>>()

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return

    const ws = new WebSocket(WS_URL)
    wsRef.current = ws

    ws.onopen = () => {
      setConnected(true)
    }

    ws.onmessage = (event) => {
      try {
        const data: unknown = JSON.parse(event.data as string)
        setMessages((prev) => {
          const next = [data, ...prev]
          return next.length > MAX_MESSAGES ? next.slice(0, MAX_MESSAGES) : next
        })
      } catch {
        // ignore non-JSON messages
      }
    }

    ws.onclose = () => {
      setConnected(false)
      reconnectTimer.current = setTimeout(connect, RECONNECT_DELAY)
    }

    ws.onerror = () => {
      ws.close()
    }
  }, [])

  const send = useCallback((data: string) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(data)
    }
  }, [])

  useEffect(() => {
    connect()
    return () => {
      clearTimeout(reconnectTimer.current)
      wsRef.current?.close()
    }
  }, [connect])

  return { connected, messages, send }
}
