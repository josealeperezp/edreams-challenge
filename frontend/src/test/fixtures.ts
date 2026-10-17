import type { CircuitState, ItineraryJson, PageJson, ProviderId, ProviderResultJson, ProviderStatusJson, RunJson, RunStatus, StatsJson } from '../api/types'

export const RUN_A = '11111111-1111-4111-8111-111111111111'
export const RUN_B = '22222222-2222-4222-8222-222222222222'

export function itinerary(overrides: Partial<ItineraryJson> = {}): ItineraryJson {
  return {
    id: 7,
    signature: 'IB845|MAD|BCN|2026-11-02T06:15:00Z',
    origin: 'MAD',
    destination: 'BCN',
    carrier: 'IB',
    departureAt: '2026-11-02T07:15:00+01:00',
    arrivalAt: '2026-11-02T08:35:00+01:00',
    stops: 0,
    basePrice: 85.5,
    price: 89.78,
    currency: 'EUR',
    bestProvider: 'BETA',
    segments: [
      { carrier: 'IB', flightNumber: 845, origin: 'MAD', destination: 'BCN', departure: '2026-11-02T07:15:00+01:00', arrival: '2026-11-02T08:35:00+01:00' },
    ],
    quotes: [
      { provider: 'ALPHA', price: 89.9 },
      { provider: 'BETA', price: 85.5 },
      { provider: 'GAMMA', price: 92 },
    ],
    confirmedByRun: RUN_B,
    updatedAt: '2026-10-17T10:00:00Z',
    ...overrides,
  }
}

export function page(items: ItineraryJson[], overrides: Partial<PageJson> = {}): PageJson {
  return {
    items,
    nextCursor: null,
    hasNext: false,
    inventory: { version: 12, approximateItineraries: 95412, latestRun: null },
    ...overrides,
  }
}

export function providerResult(ok: boolean, overrides: Partial<ProviderResultJson> = {}): ProviderResultJson {
  return ok
    ? { outcome: 'OK', latencyMs: 1234, received: 100, accepted: 98, rejected: { DEAD_LEG: 2 }, error: null, ...overrides }
    : { outcome: 'FAILED', latencyMs: 5000, received: 0, accepted: 0, rejected: {}, error: 'ProviderCallException: BETA responded HTTP 500', ...overrides }
}

export function run(status: RunStatus, overrides: Partial<RunJson> = {}): RunJson {
  const finished = status !== 'RUNNING'
  return {
    id: RUN_B,
    startedAt: '2026-10-17T10:00:00Z',
    finishedAt: finished ? '2026-10-17T10:00:13Z' : null,
    status,
    providers: finished
      ? { ALPHA: providerResult(true), BETA: status === 'PARTIAL' ? providerResult(false) : providerResult(true), GAMMA: providerResult(true) }
      : {},
    itineraries: finished && status !== 'FAILED' ? 95412 : null,
    error: null,
    ...overrides,
  }
}

export const stats: StatsJson = {
  total: 1234,
  minPrice: 45,
  maxPrice: 310,
  priceBuckets: Array.from({ length: 20 }, (_, i) => ({ from: 45 + i * 13.25, to: 45 + (i + 1) * 13.25, count: 100 - i * 4 })),
  byCarrier: [
    { carrier: 'IB', count: 500, minPrice: 45 },
    { carrier: 'VY', count: 400, minPrice: 52 },
    { carrier: 'FR', count: 334, minPrice: 48 },
  ],
  byProvider: [
    { provider: 'ALPHA', count: 400 },
    { provider: 'BETA', count: 500 },
    { provider: 'GAMMA', count: 334 },
  ],
  byStops: [
    { stops: 0, count: 1000 },
    { stops: 1, count: 234 },
  ],
}

export function providerStatus(id: ProviderId, circuit: CircuitState, overrides: Partial<ProviderStatusJson> = {}): ProviderStatusJson {
  return {
    id,
    baseUrl: `http://providers-mock:8081/${id.toLowerCase()}`,
    circuit,
    failureRate: -1,
    bufferedCalls: 0,
    lastResult: null,
    ...overrides,
  }
}
