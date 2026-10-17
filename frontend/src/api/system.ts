import { getJson } from './client'

export interface ProviderInfo {
  id: string
  baseUrl: string
  timeoutMillis: number
}

export interface SystemInfo {
  application: string
  version: string
  providers: ProviderInfo[]
}

export interface Health {
  status: string
  components?: Record<string, { status: string }>
}

export const fetchSystemInfo = () => getJson<SystemInfo>('/api/system/info')

/** Actuator answers 503 with a body when a component is DOWN; that body is still the answer. */
export async function fetchHealth(): Promise<Health> {
  const response = await fetch('/actuator/health', { headers: { Accept: 'application/json' } })
  if (!response.ok && response.status !== 503) {
    throw new Error(`/actuator/health responded ${response.status}`)
  }
  return (await response.json()) as Health
}
