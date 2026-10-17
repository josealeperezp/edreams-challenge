import { ApiError, getJson, postJson } from './client'
import type { RunJson } from './types'

/** Null before the first run ever: the API answers 404 then. */
export async function fetchLatestRun(signal?: AbortSignal): Promise<RunJson | null> {
  try {
    return await getJson<RunJson>('/api/ingestion/runs/latest', signal)
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null
    throw error
  }
}

export const fetchRuns = (limit = 10, signal?: AbortSignal) =>
  getJson<RunJson[]>(`/api/ingestion/runs?limit=${limit}`, signal)

/** 202 with the run id, or 409 (ApiError with runningRunId) while a run is in progress. */
export const startRun = () => postJson<{ runId: string }>('/api/ingestion/runs')
