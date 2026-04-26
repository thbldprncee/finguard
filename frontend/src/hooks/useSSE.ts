import { useEffect, useRef, useState } from 'react'

interface SSEEvent {
  type: string
  data: Record<string, unknown>
}

export function useSSE(url: string, enabled = true) {
  const [events, setEvents] = useState<SSEEvent[]>([])
  const [connected, setConnected] = useState(false)
  const esRef = useRef<EventSource | null>(null)

  useEffect(() => {
    if (!enabled) return

    const token = localStorage.getItem('finguard_token')
    const fullUrl = token ? `${url}?token=${token}` : url

    esRef.current = new EventSource(fullUrl)

    esRef.current.onopen = () => setConnected(true)
    esRef.current.onerror = () => setConnected(false)

    esRef.current.addEventListener('fraud.analysis.completed', (e) => {
      const data = JSON.parse(e.data) as Record<string, unknown>
      setEvents((prev) => [{ type: 'fraud.analysis.completed', data }, ...prev].slice(0, 50))
    })

    esRef.current.addEventListener('transaction.created', (e) => {
      const data = JSON.parse(e.data) as Record<string, unknown>
      setEvents((prev) => [{ type: 'transaction.created', data }, ...prev].slice(0, 50))
    })

    esRef.current.onmessage = (e) => {
      try {
        const data = JSON.parse(e.data) as Record<string, unknown>
        setEvents((prev) => [{ type: 'update', data }, ...prev].slice(0, 50))
      } catch {}
    }

    return () => {
      esRef.current?.close()
      setConnected(false)
    }
  }, [url, enabled])

  return { events, connected }
}
