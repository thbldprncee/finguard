import { useEffect, useState } from 'react'
import { getAccessToken } from '../auth/keycloak'

interface SSEEvent {
  type: string
  data: Record<string, unknown>
}

/**
 * Server-Sent Events over fetch() instead of EventSource: EventSource cannot send an
 * Authorization header, which previously pushed the token into the URL (and thus into
 * proxy logs and browser history). Reconnects with backoff and a fresh token.
 */
export function useSSE(url: string, enabled = true) {
  const [events, setEvents] = useState<SSEEvent[]>([])
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!enabled) return
    const abort = new AbortController()
    let retryDelay = 1000

    const handleFrame = (frame: string) => {
      let type = 'message'
      const dataLines: string[] = []
      for (const line of frame.split('\n')) {
        if (line.startsWith('event:')) type = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
      }
      if (dataLines.length === 0) return // comment / heartbeat
      try {
        const data = JSON.parse(dataLines.join('\n')) as Record<string, unknown>
        setEvents((prev) => [{ type, data }, ...prev].slice(0, 50))
      } catch {
        // ignore malformed frame
      }
    }

    const connect = async () => {
      while (!abort.signal.aborted) {
        try {
          const response = await fetch(url, {
            headers: { Authorization: `Bearer ${await getAccessToken()}`, Accept: 'text/event-stream' },
            signal: abort.signal,
          })
          if (!response.ok || !response.body) throw new Error(`SSE failed: ${response.status}`)
          setConnected(true)
          retryDelay = 1000

          const reader = response.body.pipeThrough(new TextDecoderStream()).getReader()
          let buffer = ''
          for (;;) {
            const { value, done } = await reader.read()
            if (done) break
            buffer += value.replace(/\r\n/g, '\n')
            let sep: number
            while ((sep = buffer.indexOf('\n\n')) >= 0) {
              handleFrame(buffer.slice(0, sep))
              buffer = buffer.slice(sep + 2)
            }
          }
        } catch {
          if (abort.signal.aborted) return
        }
        setConnected(false)
        await new Promise((resolve) => setTimeout(resolve, retryDelay))
        retryDelay = Math.min(retryDelay * 2, 30_000)
      }
    }

    connect()
    return () => {
      abort.abort()
      setConnected(false)
    }
  }, [url, enabled])

  return { events, connected }
}
