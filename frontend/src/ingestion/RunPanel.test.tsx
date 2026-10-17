import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import RunPanel from './RunPanel'
import { providerResult, run } from '../test/fixtures'

describe('RunPanel', () => {
  it('shows a running run with the providers that already finished and the rest fetching', () => {
    const running = run('RUNNING', { providers: { ALPHA: providerResult(true) } })
    render(<RunPanel run={running} connection="live" onStart={vi.fn()} starting={false} startError={null} />)

    expect(screen.getByText('RUNNING')).toHaveAttribute('data-status', 'RUNNING')
    expect(screen.getByText('100 received · 98 accepted · 2 rejected')).toBeInTheDocument()
    expect(screen.getByText('1.2 s')).toBeInTheDocument()
    expect(screen.getAllByText('fetching…')).toHaveLength(2)
    expect(screen.getByRole('button', { name: 'Run in progress…' })).toBeDisabled()
    expect(screen.getByText('live')).toBeInTheDocument()
  })

  it('shows a partial run with the failed provider, its error and the resolved count', async () => {
    const onStart = vi.fn()
    const user = userEvent.setup()
    render(<RunPanel run={run('PARTIAL')} connection="reconnecting" onStart={onStart} starting={false} startError={null} />)

    expect(screen.getByText('PARTIAL')).toBeInTheDocument()
    expect(screen.getByText('FAILED')).toBeInTheDocument()
    expect(screen.getByText('ProviderCallException: BETA responded HTTP 500')).toBeInTheDocument()
    expect(screen.getByText('· 95,412 itineraries')).toBeInTheDocument()
    expect(screen.getByText('reconnecting')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Run ingestion now' }))

    expect(onStart).toHaveBeenCalledTimes(1)
  })

  it('surfaces the start error and the no-run state', () => {
    const { rerender } = render(<RunPanel run={null} connection="connecting" onStart={vi.fn()} starting={false} startError={null} />)
    expect(screen.getByText('No run yet.')).toBeInTheDocument()

    rerender(<RunPanel run={run('COMPLETED')} connection="live" onStart={vi.fn()} starting={false} startError="An ingestion run is already in progress" />)
    expect(screen.getByText('An ingestion run is already in progress')).toHaveClass('error')
  })
})
