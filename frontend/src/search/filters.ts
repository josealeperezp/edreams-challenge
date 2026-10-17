/** The four filters the assessment asks for. Strings as typed; normalised before they hit the API or the URL. */
export interface SearchFilters {
  origin: string
  destination: string
  maxPrice: string
  carrier: string
}

export const EMPTY_FILTERS: SearchFilters = { origin: '', destination: '', maxPrice: '', carrier: '' }

export function normalize(filters: SearchFilters): SearchFilters {
  return {
    origin: filters.origin.trim().toUpperCase(),
    destination: filters.destination.trim().toUpperCase(),
    maxPrice: filters.maxPrice.trim(),
    carrier: filters.carrier.trim().toUpperCase(),
  }
}

export function isEmpty(filters: SearchFilters): boolean {
  return !filters.origin && !filters.destination && !filters.maxPrice && !filters.carrier
}

/** Client-side mirror of the API's validation so obvious mistakes never leave the browser. */
export function validate(filters: SearchFilters): string | null {
  if (filters.origin && !/^[A-Z]{3}$/.test(filters.origin)) return 'Origin must be a 3-letter IATA airport code'
  if (filters.destination && !/^[A-Z]{3}$/.test(filters.destination)) {
    return 'Destination must be a 3-letter IATA airport code'
  }
  if (filters.maxPrice && !(Number(filters.maxPrice) > 0)) return 'Max price must be a positive amount'
  if (filters.carrier && !/^[A-Z0-9]{2}$/.test(filters.carrier)) return 'Carrier must be a 2-character IATA code'
  return null
}

export function toSearchParams(filters: SearchFilters): URLSearchParams {
  const params = new URLSearchParams()
  if (filters.origin) params.set('origin', filters.origin)
  if (filters.destination) params.set('destination', filters.destination)
  if (filters.maxPrice) params.set('maxPrice', filters.maxPrice)
  if (filters.carrier) params.set('carrier', filters.carrier)
  return params
}

export function fromSearchParams(params: URLSearchParams): SearchFilters {
  return normalize({
    origin: params.get('origin') ?? '',
    destination: params.get('destination') ?? '',
    maxPrice: params.get('maxPrice') ?? '',
    carrier: params.get('carrier') ?? '',
  })
}

/** Filters live in the URL so a search is bookmarkable and survives a reload. */
export function readFiltersFromLocation(): SearchFilters {
  return fromSearchParams(new URLSearchParams(window.location.search))
}

export function writeFiltersToLocation(filters: SearchFilters): void {
  const query = toSearchParams(filters).toString()
  window.history.replaceState(null, '', `${window.location.pathname}${query ? `?${query}` : ''}`)
}
