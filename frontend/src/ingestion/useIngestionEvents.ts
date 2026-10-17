import { useCallback, useEffect, useState } from 'react'
import { useQueryClient, type InfiniteData, type QueryClient } from '@tanstack/react-query'
import type { EventJson, PageJson, ProviderId, RunJson } from '../api/types'
import { STATS_KEY } from '../charts/StatsPanel'
import { ITINERARIES_KEY } from '../search/useItinerarySearch'

export type Connection = 'connecting' | 'live' | 'reconnecting'

export interface LogEntry {
  at: string
  type: string
  provider: ProviderId | null
  run: RunJson
}

export interface IngestionLive {
  connection: Connection
  /** The run as the last event described it; null until the first event (the server replays the last one). */
  run: RunJson | null
  log: LogEntry[]
  /** A run completed while more than one page was loaded; results wait for the user to reload them. */
  inventoryStale: boolean
  reloadInventory: () => void
}

export const EVENT_TYPES = ['run-started', 'provider-finished', 'run-completed'] as const
export const RUNS_KEY = 'runs'
export const PROVIDERS_KEY = 'providers'
const LOG_SIZE = 50

/** Drops every loaded page but the first and refetches it, so results refresh in place without a jump. */
export function refreshFromFirstPage(queryClient: QueryClient): void {
  queryClient.setQueriesData<InfiniteData<PageJson, string | null>>({ queryKey: [ITINERARIES_KEY] }, (data) =>
    data ? { pages: data.pages.slice(0, 1), pageParams: data.pageParams.slice(0, 1) } : data,
  )
  void queryClient.invalidateQueries({ queryKey: [ITINERARIES_KEY] })
}

function hasMoreThanOnePage(queryClient: QueryClient): boolean {
  return queryClient
    .getQueriesData<InfiniteData<PageJson, string | null>>({ queryKey: [ITINERARIES_KEY] })
    .some(([, data]) => (data?.pages.length ?? 0) > 1)
}

/**
 * Server-Sent Events are the source of run state: run-started, provider-finished and run-completed
 * each carry the whole run. Polling elsewhere is only the fallback while the stream is down.
 */
export function useIngestionEvents(): IngestionLive {
  const queryClient = useQueryClient()
  const [connection, setConnection] = useState<Connection>('connecting')
  const [run, setRun] = useState<RunJson | null>(null)
  const [log, setLog] = useState<LogEntry[]>([])
  const [inventoryStale, setInventoryStale] = useState(false)

  useEffect(() => {
    if (typeof EventSource === 'undefined') return
    const source = new EventSource('/api/ingestion/events')

    const handle = (event: MessageEvent<string>) => {
      const data = JSON.parse(event.data) as EventJson
      setConnection('live')
      setRun(data.run)
      setLog((entries) =>
        [{ at: new Date().toISOString(), type: data.type, provider: data.provider, run: data.run }, ...entries].slice(0, LOG_SIZE),
      )
      void queryClient.invalidateQueries({ queryKey: [PROVIDERS_KEY] })
      void queryClient.invalidateQueries({ queryKey: [RUNS_KEY] })
      if (data.type === 'run-completed') {
        void queryClient.invalidateQueries({ queryKey: [STATS_KEY] })
        if (hasMoreThanOnePage(queryClient)) setInventoryStale(true)
        else refreshFromFirstPage(queryClient)
      }
    }

    for (const type of EVENT_TYPES) source.addEventListener(type, handle)
    source.onopen = () => setConnection('live')
    source.onerror = () => setConnection('reconnecting')
    return () => source.close()
  }, [queryClient])

  const reloadInventory = useCallback(() => {
    setInventoryStale(false)
    refreshFromFirstPage(queryClient)
  }, [queryClient])

  return { connection, run, log, inventoryStale, reloadInventory }
}
