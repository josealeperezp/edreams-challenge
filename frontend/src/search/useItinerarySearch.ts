import { useInfiniteQuery } from '@tanstack/react-query'
import { searchPage } from '../api/itineraries'
import type { SearchFilters } from './filters'

export const ITINERARIES_KEY = 'itineraries'

export const itinerariesKey = (filters: SearchFilters) => [ITINERARIES_KEY, filters] as const

/** Keyset "load more": each page carries the opaque cursor of the next one; no page numbers exist. */
export function useItinerarySearch(filters: SearchFilters) {
  return useInfiniteQuery({
    queryKey: itinerariesKey(filters),
    queryFn: ({ pageParam, signal }) => searchPage(filters, pageParam, signal),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasNext && last.nextCursor ? last.nextCursor : undefined),
  })
}
