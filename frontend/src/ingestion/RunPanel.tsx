import { PROVIDERS, type ProviderId, type ProviderResultJson, type RunJson } from '../api/types'
import { durationBetween, formatCount, formatLatency, formatRelative } from '../format'
import { ProviderBadge, StatusBadge } from '../ui/Badges'
import type { Connection } from './useIngestionEvents'
import { useTicker } from './useTicker'

interface Props {
  run: RunJson | null
  connection: Connection
  onStart: () => void
  starting: boolean
  startError: string | null
}

const CONNECTION_TITLES: Record<Connection, string> = {
  connecting: 'Connecting to the event stream',
  live: 'Receiving run events over Server-Sent Events',
  reconnecting: 'Event stream lost; the browser is reconnecting and the run state is polled meanwhile',
}

/** The current run as it happens: status, per-provider outcome as each one finishes, and a start button. */
export default function RunPanel({ run, connection, onStart, starting, startError }: Props) {
  const running = run?.status === 'RUNNING'
  const now = useTicker(running)

  return (
    <section className="card" aria-label="Ingestion run">
      <header className="card-head">
        <h2>Ingestion</h2>
        <span className={`live ${connection}`} title={CONNECTION_TITLES[connection]}>{connection}</span>
      </header>
      {!run && <p className="muted">No run yet.</p>}
      {run && (
        <>
          <p className="run-line">
            <StatusBadge status={run.status} />
            <span className="muted"> started {formatRelative(run.startedAt, now)}</span>
            <span className="muted">
              {' · '}
              {run.finishedAt
                ? `took ${durationBetween(run.startedAt, run.finishedAt)}`
                : `running for ${durationBetween(run.startedAt, new Date(now).toISOString())}`}
            </span>
            {run.itineraries !== null && <span> · {formatCount(run.itineraries)} itineraries</span>}
          </p>
          {run.error && <p className="error">{run.error}</p>}
          <ul className="plain providers">
            {PROVIDERS.map((id) => (
              <ProviderRow key={id} id={id} result={run.providers[id]} running={running} />
            ))}
          </ul>
          <p className="mono muted small">run {run.id}</p>
        </>
      )}
      <div className="actions">
        <button type="button" onClick={onStart} disabled={running || starting}>
          {running ? 'Run in progress…' : 'Run ingestion now'}
        </button>
        {startError && <span className="error">{startError}</span>}
      </div>
    </section>
  )
}

function ProviderRow({ id, result, running }: { id: ProviderId; result?: ProviderResultJson; running: boolean }) {
  if (!result) {
    return (
      <li>
        <ProviderBadge id={id} />
        <span className={running ? 'muted pulse' : 'muted'}>{running ? 'fetching…' : 'no result'}</span>
      </li>
    )
  }
  const rejected = Object.values(result.rejected).reduce((sum, n) => sum + n, 0)
  return (
    <li>
      <ProviderBadge id={id} />
      <span className={`badge outcome-${result.outcome.toLowerCase()}`}>{result.outcome}</span>
      <span className="muted">{formatLatency(result.latencyMs)}</span>
      <span>
        {formatCount(result.received)} received · {formatCount(result.accepted)} accepted · {formatCount(rejected)} rejected
      </span>
      {rejected > 0 && (
        <details>
          <summary>reasons</summary>
          <ul className="plain reasons">
            {Object.entries(result.rejected).map(([reason, n]) => (
              <li key={reason}>
                <span className="mono">{reason}</span> {formatCount(n)}
              </li>
            ))}
          </ul>
        </details>
      )}
      {result.error && <span className="error">{result.error}</span>}
    </li>
  )
}
