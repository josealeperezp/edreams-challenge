import { useQuery } from '@tanstack/react-query'
import { fetchStats } from '../api/itineraries'
import { errorMessage } from '../api/client'
import { formatCount, formatMoney } from '../format'
import type { SearchFilters } from '../search/filters'
import CarrierBars from './CarrierBars'
import PriceHistogram from './PriceHistogram'
import ProviderShare from './ProviderShare'
import { useElementWidth } from './useElementWidth'

export const STATS_KEY = 'stats'

/** Charts over everything the filters match, from the stats endpoint, never from the loaded pages. */
export default function StatsPanel({ filters }: { filters: SearchFilters }) {
  const stats = useQuery({
    queryKey: [STATS_KEY, filters],
    queryFn: ({ signal }) => fetchStats(filters, signal),
  })
  const [ref, width] = useElementWidth<HTMLDivElement>()
  const columns = width >= 900 ? 3 : width >= 580 ? 2 : 1
  const chartWidth = Math.max(240, Math.floor((width - 16 * (columns - 1)) / columns) - 34)
  const data = stats.data

  return (
    <section className="card stats" aria-label="Inventory statistics">
      <header className="stats-head">
        {data ? (
          <h2>
            {formatCount(data.total)} {data.total === 1 ? 'itinerary matches' : 'itineraries match'}
          </h2>
        ) : (
          <h2 className="muted">{stats.isError ? 'Statistics unavailable' : 'Counting…'}</h2>
        )}
        {data && data.total > 0 && data.minPrice !== null && data.maxPrice !== null && (
          <p className="muted">
            from {formatMoney(data.minPrice)} to {formatMoney(data.maxPrice)}, OTA markup included
          </p>
        )}
        {stats.isError && <p className="error">{errorMessage(stats.error)}</p>}
      </header>
      <div className="charts" ref={ref} style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}>
        {data && data.total > 0 && (
          <>
            <figure aria-label="Price distribution">
              <figcaption>Price distribution</figcaption>
              <PriceHistogram buckets={data.priceBuckets} width={chartWidth} />
            </figure>
            <figure aria-label="Cheapest provider">
              <figcaption>Cheapest provider</figcaption>
              <ProviderShare byProvider={data.byProvider} width={chartWidth} />
            </figure>
            <figure aria-label="Carriers">
              <figcaption>Carriers, top {data.byCarrier.length}</figcaption>
              <CarrierBars byCarrier={data.byCarrier} width={chartWidth} />
            </figure>
          </>
        )}
      </div>
    </section>
  )
}
