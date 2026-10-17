import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'

export function createTestClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { retry: false } },
  })
}

export function renderWithQuery(ui: ReactElement, client: QueryClient = createTestClient()) {
  return { client, ...render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>) }
}
