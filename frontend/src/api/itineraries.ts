import { getJson } from './client'
import type { PageJson, StatsJson } from './types'
import { toSearchParams, type SearchFilters } from '../search/filters'

export const PAGE_SIZE = 20

/** One keyset page: the cursor is the opaque position returned by the previous page. */
export function searchPage(filters: SearchFilters, cursor: string | null, signal?: AbortSignal): Promise<PageJson> {
  const params = toSearchParams(filters)
  params.set('limit', String(PAGE_SIZE))
  if (cursor) params.set('cursor', cursor)
  return getJson<PageJson>(`/api/itineraries?${params.toString()}`, signal)
}

/** Aggregates over everything the filters match, not over the loaded pages. */
export function fetchStats(filters: SearchFilters, signal?: AbortSignal): Promise<StatsJson> {
  const query = toSearchParams(filters).toString()
  return getJson<StatsJson>(`/api/itineraries/stats${query ? `?${query}` : ''}`, signal)
}
