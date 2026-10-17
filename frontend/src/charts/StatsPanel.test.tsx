import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import StatsPanel from './StatsPanel'
import { EMPTY_FILTERS } from '../search/filters'
import { installFetch, requested } from '../test/fetchMock'
import { stats } from '../test/fixtures'
import { renderWithQuery } from '../test/render'

describe('StatsPanel', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('asks the stats endpoint with the filters and draws the three charts over the whole match', async () => {
    const fetchMock = installFetch({ 'GET /api/itineraries/stats': () => stats })

    renderWithQuery(<StatsPanel filters={{ ...EMPTY_FILTERS, origin: 'MAD', maxPrice: '300' }} />)

    expect(await screen.findByText('1,234 itineraries match')).toBeInTheDocument()
    expect(screen.getByText('from €45.00 to €310.00, OTA markup included')).toBeInTheDocument()
    expect(screen.getByLabelText('Price distribution')).toBeInTheDocument()
    expect(screen.getByLabelText('Cheapest provider')).toBeInTheDocument()
    expect(screen.getByLabelText('Carriers')).toHaveTextContent('Carriers, top 3')
    const [url] = requested(fetchMock, '/api/itineraries/stats')
    expect(url.search).toBe('?origin=MAD&maxPrice=300')
  })

  it('shows an empty match without charts', async () => {
    installFetch({ 'GET /api/itineraries/stats': () => ({ ...stats, total: 0, minPrice: null, maxPrice: null, priceBuckets: [], byCarrier: [], byProvider: [], byStops: [] }) })

    renderWithQuery(<StatsPanel filters={EMPTY_FILTERS} />)

    expect(await screen.findByText('0 itineraries match')).toBeInTheDocument()
    expect(screen.queryByLabelText('Price distribution')).toBeNull()
  })
})
