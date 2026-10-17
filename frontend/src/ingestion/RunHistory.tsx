import { PROVIDERS, type RunJson } from '../api/types'
import { durationBetween, formatCount, formatRelative } from '../format'
import { StatusBadge } from '../ui/Badges'

/** The last runs: the daily refresh, with which providers made it into each one. */
export default function RunHistory({ runs }: { runs: RunJson[] | undefined }) {
  return (
    <section className="card" aria-label="Run history">
      <h2>Recent runs</h2>
      {!runs?.length && <p className="muted">No runs recorded.</p>}
      {!!runs?.length && (
        <table className="history">
          <thead>
            <tr>
              <th>Started</th>
              <th>Status</th>
              <th>Took</th>
              <th className="num">Itineraries</th>
              <th>Providers</th>
            </tr>
          </thead>
          <tbody>
            {runs.map((run) => (
              <tr key={run.id}>
                <td title={run.startedAt}>{formatRelative(run.startedAt)}</td>
                <td><StatusBadge status={run.status} /></td>
                <td>{run.finishedAt ? durationBetween(run.startedAt, run.finishedAt) : '…'}</td>
                <td className="num">{run.itineraries === null ? '' : formatCount(run.itineraries)}</td>
                <td className="dots">
                  {PROVIDERS.map((id) => {
                    const result = run.providers[id]
                    const state = !result ? 'none' : result.outcome === 'OK' ? 'ok' : 'failed'
                    return <span key={id} className={`dot ${state}`} title={`${id}: ${result ? result.outcome : 'no result'}`} />
                  })}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
