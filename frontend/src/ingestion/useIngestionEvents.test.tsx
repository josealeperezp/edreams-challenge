import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useIngestionEvents } from './useIngestionEvents'
import { STATS_KEY } from '../charts/StatsPanel'
import { EMPTY_FILTERS } from '../search/filters'
import { ITINERARIES_KEY, itinerariesKey } from '../search/useItinerarySearch'
import { FakeEventSource } from '../test/fakeEventSource'
import { itinerary, page, run } from '../test/fixtures'
import { createTestClient, renderWithQuery } from '../test/render'

function Harness() {
  const live = useIngestionEvents()
  return (
    <div>
      <span data-testid="connection">{live.connection}</span>
      <span data-testid="status">{live.run?.status ?? 'none'}</span>
      <span data-testid="events">{live.log.map((entry) => entry.type).join(',')}</span>
      <span data-testid="stale">{String(live.inventoryStale)}</span>
      <button type="button" onClick={live.reloadInventory}>reload</button>
    </div>
  )
}

describe('useIngestionEvents', () => {
  beforeEach(() => FakeEventSource.install())
  afterEach(() => vi.unstubAllGlobals())

  it('subscribes to the event stream and follows the run through its events', () => {
    renderWithQuery(<Harness />)
    const source = FakeEventSource.latest()
    expect(source.url).toBe('/api/ingestion/events')
    expect(screen.getByTestId('connection')).toHaveTextContent('connecting')

    act(() => source.open())
    expect(screen.getByTestId('connection')).toHaveTextContent('live')

    act(() => source.emit('run-started', { type: 'run-started', provider: null, run: run('RUNNING') }))
    act(() => source.emit('provider-finished', { type: 'provider-finished', provider: 'ALPHA', run: run('RUNNING') }))
    expect(screen.getByTestId('status')).toHaveTextContent('RUNNING')
    expect(screen.getByTestId('events')).toHaveTextContent('provider-finished,run-started')

    act(() => source.fail())
    expect(screen.getByTestId('connection')).toHaveTextContent('reconnecting')
  })

  it('refreshes a one-page search in place and only flags a multi-page one', async () => {
    const client = createTestClient()
    const key = itinerariesKey(EMPTY_FILTERS)
    client.setQueryData(key, { pages: [page([itinerary()])], pageParams: [null] })
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    const user = userEvent.setup()
    renderWithQuery(<Harness />, client)
    const completed = { type: 'run-completed', provider: null, run: run('COMPLETED') }

    act(() => FakeEventSource.latest().emit('run-completed', completed))

    expect(screen.getByTestId('stale')).toHaveTextContent('false')
    expect(invalidate).toHaveBeenCalledWith({ queryKey: [ITINERARIES_KEY] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: [STATS_KEY] })

    client.setQueryData(key, { pages: [page([itinerary()]), page([itinerary({ id: 8 })])], pageParams: [null, 'cursor-1'] })
    act(() => FakeEventSource.latest().emit('run-completed', completed))

    expect(screen.getByTestId('stale')).toHaveTextContent('true')
    expect(client.getQueryData(key)).toMatchObject({ pageParams: [null, 'cursor-1'] })

    await user.click(screen.getByRole('button', { name: 'reload' }))

    expect(screen.getByTestId('stale')).toHaveTextContent('false')
    expect(client.getQueryData(key)).toMatchObject({ pageParams: [null] })
  })

  it('closes the stream on unmount', () => {
    const { unmount } = renderWithQuery(<Harness />)
    unmount()
    expect(FakeEventSource.latest().closed).toBe(true)
  })
})
