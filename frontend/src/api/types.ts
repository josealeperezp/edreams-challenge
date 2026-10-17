/** Mirrors of the backend's JSON records (backend/infrastructure/.../json). */
export type ProviderId = 'ALPHA' | 'BETA' | 'GAMMA'
export const PROVIDERS: ProviderId[] = ['ALPHA', 'BETA', 'GAMMA']

export type RunStatus = 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED'
export type CircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN' | 'DISABLED' | 'FORCED_OPEN' | 'METRICS_ONLY'

export interface SegmentJson {
  carrier: string
  flightNumber: number
  origin: string
  destination: string
  departure: string
  arrival: string
}

export interface QuoteJson {
  provider: ProviderId
  price: number
}

export interface ItineraryJson {
  id: number
  signature: string
  origin: string
  destination: string
  carrier: string
  departureAt: string
  arrivalAt: string
  stops: number
  basePrice: number
  price: number
  currency: string
  bestProvider: ProviderId
  segments: SegmentJson[]
  quotes: QuoteJson[]
  confirmedByRun: string | null
  updatedAt: string
}

export interface ProviderResultJson {
  outcome: 'OK' | 'FAILED'
  latencyMs: number
  received: number
  accepted: number
  rejected: Record<string, number>
  error: string | null
}

export interface RunJson {
  id: string
  startedAt: string
  finishedAt: string | null
  status: RunStatus
  providers: Partial<Record<ProviderId, ProviderResultJson>>
  itineraries: number | null
  error: string | null
}

export interface InventoryJson {
  version: number
  approximateItineraries: number
  latestRun: RunJson | null
}

export interface PageJson {
  items: ItineraryJson[]
  nextCursor: string | null
  hasNext: boolean
  inventory: InventoryJson
}

export interface BucketJson {
  from: number
  to: number
  count: number
}

export interface StatsJson {
  total: number
  minPrice: number | null
  maxPrice: number | null
  priceBuckets: BucketJson[]
  byCarrier: { carrier: string; count: number; minPrice: number }[]
  byProvider: { provider: ProviderId; count: number }[]
  byStops: { stops: number; count: number }[]
}

export interface ProviderStatusJson {
  id: ProviderId
  baseUrl: string
  circuit: CircuitState
  failureRate: number
  bufferedCalls: number
  lastResult: ProviderResultJson | null
}

export type IngestionEventType = 'run-started' | 'provider-finished' | 'run-completed'

export interface EventJson {
  type: IngestionEventType
  provider: ProviderId | null
  run: RunJson
}
