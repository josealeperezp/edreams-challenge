import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import ResultsTable from './ResultsTable'
import { RUN_A, RUN_B, itinerary, page } from '../test/fixtures'

describe('ResultsTable', () => {
  it('shows the marked-up price, the base price and every provider quote with the winner highlighted', () => {
    render(<ResultsTable pages={[page([itinerary()])]} referenceRunId={RUN_B} hasNext={false} loadingMore={false} onLoadMore={vi.fn()} />)

    expect(screen.getByText('€89.78')).toHaveClass('price')
    expect(screen.getByText('€85.50')).toBeInTheDocument()
    expect(screen.getByText('BETA 85.50')).toHaveClass('winner')
    expect(screen.getByText('ALPHA 89.90')).not.toHaveClass('winner')
    expect(screen.getByText('GAMMA 92.00')).toBeInTheDocument()
    expect(screen.getByText('02 Nov 07:15 +01:00')).toBeInTheDocument()
    expect(screen.getByText('IB845')).toBeInTheDocument()
    expect(screen.getByText('1h 20m')).toBeInTheDocument()
    expect(screen.queryByText('stale')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Load more' })).toBeNull()
    expect(screen.getByText('1 loaded · sorted by price, then id')).toBeInTheDocument()
  })

  it('marks rows last confirmed by an older run as stale', () => {
    render(<ResultsTable pages={[page([itinerary({ confirmedByRun: RUN_A })])]} referenceRunId={RUN_B} hasNext={false} loadingMore={false} onLoadMore={vi.fn()} />)

    expect(screen.getByText('stale')).toHaveAttribute('title', expect.stringContaining(RUN_A))
  })

  it('offers "Load more" while a next cursor exists and requests it', async () => {
    const onLoadMore = vi.fn()
    const user = userEvent.setup()
    const first = page([itinerary()], { nextCursor: 'ODkuNzg6Nw', hasNext: true })
    const second = page([itinerary({ id: 8, price: 91.0, basePrice: 86.67 })])
    render(<ResultsTable pages={[first, second]} referenceRunId={null} hasNext={true} loadingMore={false} onLoadMore={onLoadMore} />)

    expect(screen.getByText('2 loaded · sorted by price, then id')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Load more' }))

    expect(onLoadMore).toHaveBeenCalledTimes(1)
  })

  it('shows the opaque cursor of the last page and expands the segments on demand', async () => {
    const user = userEvent.setup()
    render(<ResultsTable pages={[page([itinerary()], { nextCursor: 'ODkuNzg6Nw', hasNext: true })]} referenceRunId={null} hasNext={true} loadingMore={true} onLoadMore={vi.fn()} />)

    expect(screen.getByText('cursor ODkuNzg6Nw')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Loading…' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'segments' }))

    expect(screen.getByText(/signature IB845\|MAD\|BCN/)).toBeInTheDocument()
    expect(screen.getByRole('list')).toHaveTextContent('IB845 MAD 02 Nov 07:15 +01:00 → BCN 02 Nov 08:35 +01:00')
  })

  it('says so when nothing matches', () => {
    render(<ResultsTable pages={[page([])]} referenceRunId={null} hasNext={false} loadingMore={false} onLoadMore={vi.fn()} />)

    expect(screen.getByText('No itineraries match these filters.')).toBeInTheDocument()
  })
})
