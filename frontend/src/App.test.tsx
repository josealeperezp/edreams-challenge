import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { PROVIDERS } from './api/types'
import { FakeEventSource } from './test/fakeEventSource'
import { installFetch, problem, requested, type FetchMock } from './test/fetchMock'
import { RUN_A, itinerary, page, providerResult, providerStatus, run, stats } from './test/fixtures'
import { renderWithQuery } from './test/render'

describe('App', () => {
  let fetchMock: FetchMock

  beforeEach(() => {
    window.history.replaceState(null, '', '/')
    FakeEventSource.install()
    fetchMock = installFetch({
      'GET /api/itineraries/stats': () => stats,
      'GET /api/itineraries': () => page([itinerary()], { inventory: { version: 12, approximateItineraries: 95412, latestRun: run('COMPLETED') } }),
      'GET /api/ingestion/runs/latest': () => run('COMPLETED'),
      'GET /api/ingestion/runs': () => [run('COMPLETED')],
      'GET /api/providers/status': () => PROVIDERS.map((id) => providerStatus(id, 'CLOSED')),
      'GET /actuator/health': () => ({ status: 'UP', components: { db: { status: 'UP' }, redis: { status: 'UP' } } }),
      'POST /api/ingestion/runs': () => problem(409, 'An ingestion run is already in progress', { runningRunId: RUN_A }),
    })
  })

  afterEach(() => vi.unstubAllGlobals())

  it('shows results, statistics, inventory freshness and provider health on one page', async () => {
    renderWithQuery(<App />)

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Flight Aggregation Dashboard')
    expect(await screen.findByText('€89.78')).toBeInTheDocument()
    expect(await screen.findByText('1,234 itineraries match')).toBeInTheDocument()
    expect(await screen.findByText(/inventory v12 · 95,412 itineraries · last run completed/)).toBeInTheDocument()
    expect(await screen.findByText(/backend up/)).toBeInTheDocument()
    expect(await screen.findAllByText('CLOSED')).toHaveLength(3)
    expect(screen.getAllByText('COMPLETED').length).toBeGreaterThanOrEqual(2)
  })

  it('searches with the form, puts the filters in the URL and shows the API problem on bad input', async () => {
    const user = userEvent.setup()
    renderWithQuery(<App />)
    await screen.findByText('€89.78')

    await user.type(screen.getByLabelText('Origin'), 'MAD')
    await user.type(screen.getByLabelText('Destination'), 'BCN')
    await user.click(screen.getByRole('button', { name: 'Search' }))

    expect(window.location.search).toBe('?origin=MAD&destination=BCN')
    const pages = requested(fetchMock, '/api/itineraries')
    expect(pages.at(-1)?.search).toContain('origin=MAD&destination=BCN&limit=20')
  })

  it('follows a run live and surfaces a 409 when a start conflicts', async () => {
    const user = userEvent.setup()
    renderWithQuery(<App />)
    await screen.findByText('€89.78')
    const source = FakeEventSource.latest()

    act(() => source.emit('run-started', { type: 'run-started', provider: null, run: run('RUNNING') }))
    expect(await screen.findByRole('button', { name: 'Run in progress…' })).toBeDisabled()

    act(() => source.emit('provider-finished', { type: 'provider-finished', provider: 'ALPHA', run: run('RUNNING', { providers: { ALPHA: providerResult(true) } }) }))
    expect(await screen.findByText('100 received · 98 accepted · 2 rejected')).toBeInTheDocument()

    act(() => source.emit('run-completed', { type: 'run-completed', provider: null, run: run('COMPLETED', { itineraries: 95500 }) }))
    expect(await screen.findByText('· 95,500 itineraries')).toBeInTheDocument()
    expect(screen.queryByRole('status')).toBeNull()

    await user.click(screen.getByRole('button', { name: 'Run ingestion now' }))
    expect(await screen.findByText('An ingestion run is already in progress')).toBeInTheDocument()
  })
})
