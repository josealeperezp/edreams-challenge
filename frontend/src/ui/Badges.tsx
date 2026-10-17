import type { CircuitState, ProviderId, RunStatus } from '../api/types'

export function ProviderBadge({ id }: { id: ProviderId }) {
  return <span className={`badge provider provider-${id}`}>{id}</span>
}

export function StatusBadge({ status }: { status: RunStatus }) {
  return (
    <span className={`badge status-${status.toLowerCase()}`} data-status={status}>
      {status}
    </span>
  )
}

const CIRCUIT_TITLES: Record<CircuitState, string> = {
  CLOSED: 'Calls flow normally',
  OPEN: 'Breaker open: calls fail fast until the wait window ends',
  HALF_OPEN: 'Probing the provider with a few calls',
  DISABLED: 'Breaker disabled',
  FORCED_OPEN: 'Breaker forced open',
  METRICS_ONLY: 'Metrics only',
}

export function CircuitBadge({ state }: { state: CircuitState }) {
  return (
    <span className={`badge circuit-${state.toLowerCase()}`} title={CIRCUIT_TITLES[state]} data-circuit={state}>
      {state.replace('_', ' ')}
    </span>
  )
}
