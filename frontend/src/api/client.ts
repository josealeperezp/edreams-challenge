/** Thin fetch wrapper: JSON in, JSON out, RFC 9457 problem details turned into ApiError. */
export class ApiError extends Error {
  readonly status: number
  readonly detail: string
  readonly runningRunId?: string

  constructor(status: number, detail: string, runningRunId?: string) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.detail = detail
    this.runningRunId = runningRunId
  }
}

async function problemFrom(response: Response): Promise<ApiError> {
  let detail = `${response.status} ${response.statusText}`.trim()
  let runningRunId: string | undefined
  try {
    const body = (await response.json()) as { detail?: unknown; runningRunId?: unknown }
    if (typeof body?.detail === 'string') detail = body.detail
    if (typeof body?.runningRunId === 'string') runningRunId = body.runningRunId
  } catch {
    // not a problem detail body; keep the status line
  }
  return new ApiError(response.status, detail, runningRunId)
}

export async function getJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(url, { headers: { Accept: 'application/json' }, signal })
  if (!response.ok) throw await problemFrom(response)
  return (await response.json()) as T
}

export async function postJson<T>(url: string): Promise<T> {
  const response = await fetch(url, { method: 'POST', headers: { Accept: 'application/json' } })
  if (!response.ok) throw await problemFrom(response)
  return (await response.json()) as T
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.detail
  if (error instanceof Error) return error.message
  return String(error)
}
