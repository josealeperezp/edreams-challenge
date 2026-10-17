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

async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url, { headers: { Accept: 'application/json' } })
  if (!response.ok) {
    throw new Error(`${url} responded ${response.status}`)
  }
  return (await response.json()) as T
}

export const fetchSystemInfo = () => getJson<SystemInfo>('/api/system/info')

export const fetchHealth = () => getJson<Health>('/actuator/health')
