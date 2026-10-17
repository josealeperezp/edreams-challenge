import { getJson } from './client'
import type { ProviderStatusJson } from './types'

export const fetchProviderStatus = (signal?: AbortSignal) =>
  getJson<ProviderStatusJson[]>('/api/providers/status', signal)
