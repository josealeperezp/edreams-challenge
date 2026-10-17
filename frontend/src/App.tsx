import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from './api/client'
import { fetchLatestRun, fetchRuns, startRun } from './api/ingestion'
import { fetchHealth } from './api/system'
import StatsPanel from './charts/StatsPanel'
import { formatCount, formatRelative } from './format'
import ProviderStatusPanel from './ingestion/ProviderStatusPanel'
import RunHistory from './ingestion/RunHistory'
import RunPanel from './ingestion/RunPanel'
import { RUNS_KEY, useIngestionEvents } from './ingestion/useIngestionEvents'
import { readFiltersFromLocation, writeFiltersToLocation, type SearchFilters } from './search/filters'
import ResultsTable from './search/ResultsTable'
import SearchForm from './search/SearchForm'
import { useItinerarySearch } from './search/useItinerarySearch'

export default function App() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<SearchFilters>(readFiltersFromLocation)
  const search = useItinerarySearch(filters)
  const live = useIngestionEvents()

  const runs = useQuery({ queryKey: [RUNS_KEY, 'list'], queryFn: ({ signal }) => fetchRuns(10, signal) })
  const latest = useQuery({
    queryKey: [RUNS_KEY, 'latest'],
    queryFn: ({ signal }) => fetchLatestRun(signal),
    refetchInterval: live.connection === 'live' ? false : 5000,
  })
  const health = useQuery({ queryKey: ['health'], queryFn: fetchHealth, refetchInterval: 5000 })
  const start = useMutation({
    mutationFn: startRun,
    onSettled: () => queryClient.invalidateQueries({ queryKey: [RUNS_KEY] }),
  })

  const run = live.run ?? latest.data ?? null
  const running = run?.status === 'RUNNING'
  const referenceRunId = runs.data?.find((r) => r.status === 'COMPLETED' || r.status === 'PARTIAL')?.id ?? null
  const inventory = search.data?.pages[0]?.inventory
  const healthUp = health.data?.status === 'UP'

  const onSearch = (next: SearchFilters) => {
    setFilters(next)
    writeFiltersToLocation(next)
  }

  return (
    <main className="container">
      <header className="page-head">
        <div>
          <h1>Flight Aggregation Dashboard</h1>
          <p className="muted inventory-line">
            {inventory ? (
              <>
                inventory v{inventory.version} · {formatCount(inventory.approximateItineraries)} itineraries
                {inventory.latestRun && (
                  <>
                    {' · '}last run {inventory.latestRun.status.toLowerCase()}{' '}
                    {formatRelative(inventory.latestRun.finishedAt ?? inventory.latestRun.startedAt)}
                  </>
                )}
              </>
            ) : (
              'Deduplicated, marked-up itineraries from three providers'
            )}
          </p>
        </div>
        <span className={`health ${health.isError ? 'down' : healthUp ? 'up' : 'down'}`}
          title={health.data ? Object.entries(health.data.components ?? {}).map(([k, v]) => `${k}: ${v.status}`).join(', ') : ''}>
          <span className={`dot ${healthUp ? 'ok' : 'failed'}`} />
          backend {health.isError ? 'unreachable' : (health.data?.status ?? '…').toLowerCase()}
        </span>
      </header>

      <div className="layout">
        <div className="column">
          <section className="card" aria-label="Search">
            <SearchForm initial={filters} onSearch={onSearch} serverError={search.isError ? errorMessage(search.error) : null} />
          </section>

          <StatsPanel filters={filters} />

          <section className="card" aria-label="Results">
            {live.inventoryStale && (
              <div className="banner" role="status">
                <span>A run just completed and the inventory changed.</span>
                <button type="button" onClick={live.reloadInventory}>Reload results</button>
              </div>
            )}
            {search.isPending && <p className="muted">Searching…</p>}
            {search.isError && <p className="error">{errorMessage(search.error)}</p>}
            {search.data && (
              <ResultsTable
                pages={search.data.pages}
                referenceRunId={referenceRunId}
                hasNext={search.hasNextPage}
                loadingMore={search.isFetchingNextPage}
                onLoadMore={() => void search.fetchNextPage()}
              />
            )}
          </section>
        </div>

        <aside className="column">
          <RunPanel
            run={run}
            connection={live.connection}
            onStart={() => start.mutate()}
            starting={start.isPending}
            startError={start.isError ? errorMessage(start.error) : null}
          />
          <ProviderStatusPanel running={running} />
          <RunHistory runs={runs.data} />
        </aside>
      </div>
    </main>
  )
}
