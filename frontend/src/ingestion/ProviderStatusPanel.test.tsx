import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ProviderStatusPanel from './ProviderStatusPanel'
import { installFetch } from '../test/fetchMock'
import { providerResult, providerStatus } from '../test/fixtures'
import { renderWithQuery } from '../test/render'

describe('ProviderStatusPanel', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows each breaker state with its failure window and last outcome', async () => {
    installFetch({
      'GET /api/providers/status': () => [
        providerStatus('ALPHA', 'CLOSED', { lastResult: providerResult(true) }),
        providerStatus('BETA', 'OPEN', { failureRate: 75, bufferedCalls: 4, lastResult: providerResult(false) }),
        providerStatus('GAMMA', 'HALF_OPEN', { failureRate: -1, bufferedCalls: 2 }),
      ],
    })

    renderWithQuery(<ProviderStatusPanel running={false} />)

    expect(await screen.findByText('OPEN')).toHaveAttribute('data-circuit', 'OPEN')
    expect(screen.getByText('CLOSED')).toHaveAttribute('data-circuit', 'CLOSED')
    expect(screen.getByText('HALF OPEN')).toHaveAttribute('data-circuit', 'HALF_OPEN')
    expect(screen.getByText('75% failures over 4 calls')).toBeInTheDocument()
    expect(screen.getByText('2 calls, window not full')).toBeInTheDocument()
    expect(screen.getByText('no calls in window')).toBeInTheDocument()
    expect(screen.getByText('last run ok in 1.2 s')).toBeInTheDocument()
    expect(screen.getByText('last run failed in 5.0 s')).toBeInTheDocument()
  })
})
