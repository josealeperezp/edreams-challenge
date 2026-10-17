import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const systemInfo = {
  application: 'flights-backend',
  version: 'test',
  providers: [
    { id: 'ALPHA', baseUrl: 'http://alpha', timeoutMillis: 3000 },
    { id: 'BETA', baseUrl: 'http://beta', timeoutMillis: 3000 },
    { id: 'GAMMA', baseUrl: 'http://gamma', timeoutMillis: 3000 },
  ],
}

const health = { status: 'UP', components: { db: { status: 'UP' }, redis: { status: 'UP' } } }

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) =>
        Promise.resolve(url.startsWith('/api/system/info') ? jsonResponse(systemInfo) : jsonResponse(health)),
      ),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows backend health and configured providers', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <App />
      </QueryClientProvider>,
    )

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Flight Aggregation Dashboard')
    expect(await screen.findByText('UP', { selector: '.status' })).toBeInTheDocument()
    expect(await screen.findByText('GAMMA')).toBeInTheDocument()
  })
})
