import { Fragment, useState } from 'react'
import type { ItineraryJson, PageJson, SegmentJson } from '../api/types'
import { durationBetween, formatMoney, formatWallClock } from '../format'
import { ProviderBadge } from '../ui/Badges'

interface Props {
  pages: PageJson[]
  /** The newest finished run; rows last confirmed by an older run survived a provider failure. */
  referenceRunId: string | null
  hasNext: boolean
  loadingMore: boolean
  onLoadMore: () => void
}

export default function ResultsTable({ pages, referenceRunId, hasNext, loadingMore, onLoadMore }: Props) {
  const [expanded, setExpanded] = useState<ReadonlySet<number>>(new Set())
  const items = pages.flatMap((page) => page.items)
  const nextCursor = pages.at(-1)?.nextCursor ?? null

  const toggle = (id: number) =>
    setExpanded((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  if (items.length === 0) {
    return <p className="muted empty">No itineraries match these filters.</p>
  }

  return (
    <div className="results">
      <table>
        <thead>
          <tr>
            <th>Departure</th>
            <th>Route</th>
            <th>Flights</th>
            <th>Duration</th>
            <th>Cheapest</th>
            <th className="num">Base</th>
            <th className="num">Price</th>
            <th>Quotes</th>
          </tr>
        </thead>
        <tbody>
          {items.map((it) => {
            const stale = referenceRunId !== null && it.confirmedByRun !== null && it.confirmedByRun !== referenceRunId
            const open = expanded.has(it.id)
            return (
              <Fragment key={it.id}>
                <tr data-id={it.id} className={stale ? 'stale-row' : undefined}>
                  <td>{formatWallClock(it.departureAt)}</td>
                  <td>
                    <Route itinerary={it} />
                    {stale && (
                      <span className="badge stale" title={`Last confirmed by run ${it.confirmedByRun}; not seen in the newest run`}>
                        stale
                      </span>
                    )}
                  </td>
                  <td className="mono">{it.segments.map((s) => `${s.carrier}${s.flightNumber}`).join(' · ')}</td>
                  <td>
                    {durationBetween(it.departureAt, it.arrivalAt)}
                    <span className="muted"> · {it.stops === 0 ? 'direct' : `${it.stops} stop${it.stops > 1 ? 's' : ''}`}</span>
                  </td>
                  <td><ProviderBadge id={it.bestProvider} /></td>
                  <td className="num muted">{formatMoney(it.basePrice, it.currency)}</td>
                  <td className="num price">{formatMoney(it.price, it.currency)}</td>
                  <td>
                    <Quotes itinerary={it} />
                    <button type="button" className="link" onClick={() => toggle(it.id)} aria-expanded={open}>
                      {open ? 'hide' : 'segments'}
                    </button>
                  </td>
                </tr>
                {open && (
                  <tr className="segments">
                    <td colSpan={8}>
                      <SegmentList segments={it.segments} />
                      <span className="mono muted">signature {it.signature}</span>
                    </td>
                  </tr>
                )}
              </Fragment>
            )
          })}
        </tbody>
      </table>
      <footer className="results-footer">
        <span className="muted">{items.length} loaded · sorted by price, then id</span>
        {nextCursor && (
          <span className="mono muted" title="Opaque keyset cursor: price and id of the last row, base64url">
            cursor {nextCursor.length > 18 ? `${nextCursor.slice(0, 18)}…` : nextCursor}
          </span>
        )}
        {hasNext && (
          <button type="button" onClick={onLoadMore} disabled={loadingMore}>
            {loadingMore ? 'Loading…' : 'Load more'}
          </button>
        )}
      </footer>
    </div>
  )
}

function Route({ itinerary }: { itinerary: ItineraryJson }) {
  const via = itinerary.segments.slice(0, -1).map((s) => s.destination)
  return (
    <span className="route">
      <strong>{itinerary.origin}</strong> → <strong>{itinerary.destination}</strong>
      {via.length > 0 && <span className="muted"> via {via.join(', ')}</span>}
    </span>
  )
}

/** Every provider's price for this itinerary: the dedup and cheapest-wins rules, visible on each row. */
function Quotes({ itinerary }: { itinerary: ItineraryJson }) {
  const sorted = [...itinerary.quotes].sort((a, b) => a.price - b.price)
  return (
    <span className="quotes">
      {sorted.map((quote) => (
        <span key={quote.provider}
          className={`chip provider-${quote.provider}${quote.provider === itinerary.bestProvider ? ' winner' : ''}`}
          title={`${quote.provider} quoted ${formatMoney(quote.price, itinerary.currency)}`}>
          {quote.provider} {quote.price.toFixed(2)}
        </span>
      ))}
    </span>
  )
}

function SegmentList({ segments }: { segments: SegmentJson[] }) {
  return (
    <ol className="segment-list">
      {segments.map((s) => (
        <li key={`${s.carrier}${s.flightNumber}-${s.departure}`}>
          <span className="mono">{s.carrier}{s.flightNumber}</span> {s.origin} {formatWallClock(s.departure)} → {s.destination}{' '}
          {formatWallClock(s.arrival)}
        </li>
      ))}
    </ol>
  )
}
