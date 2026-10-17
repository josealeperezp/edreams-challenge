import { useQuery } from '@tanstack/react-query'
import { fetchProviderStatus } from '../api/providers'
import type { ProviderStatusJson } from '../api/types'
import { formatLatency } from '../format'
import { CircuitBadge, ProviderBadge } from '../ui/Badges'
import { PROVIDERS_KEY } from './useIngestionEvents'

/** Circuit breaker state per provider. Polled fast while a run is on or a breaker is not CLOSED. */
export default function ProviderStatusPanel({ running }: { running: boolean }) {
  const status = useQuery({
    queryKey: [PROVIDERS_KEY, 'status'],
    queryFn: ({ signal }) => fetchProviderStatus(signal),
    refetchInterval: (query) => {
      const unhealthy = query.state.data?.some((p) => p.circuit !== 'CLOSED') ?? false
      return running || unhealthy ? 3000 : 30000
    },
  })

  return (
    <section className="card" aria-label="Provider circuits">
      <h2>Providers</h2>
      {status.isError && <p className="error">Could not load provider status</p>}
      <ul className="plain providers">
        {status.data?.map((provider) => (
          <li key={provider.id}>
            <ProviderBadge id={provider.id} />
            <CircuitBadge state={provider.circuit} />
            <span className="muted">{window_(provider)}</span>
            {provider.lastResult && (
              <span className="muted">
                last run {provider.lastResult.outcome.toLowerCase()} in {formatLatency(provider.lastResult.latencyMs)}
              </span>
            )}
            <span className="mono muted small">{provider.baseUrl}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

function window_(provider: ProviderStatusJson): string {
  if (provider.bufferedCalls === 0) return 'no calls in window'
  if (provider.failureRate < 0) return `${provider.bufferedCalls} call${provider.bufferedCalls > 1 ? 's' : ''}, window not full`
  return `${Math.round(provider.failureRate)}% failures over ${provider.bufferedCalls} calls`
}
