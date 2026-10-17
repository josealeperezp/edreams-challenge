import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, errorMessage, getJson } from './client'
import { fetchLatestRun, startRun } from './ingestion'
import { installFetch, problem } from '../test/fetchMock'
import { RUN_A } from '../test/fixtures'

describe('api client', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('turns a problem detail into an ApiError carrying the detail', async () => {
    installFetch({ 'GET /api/itineraries': () => problem(400, 'Invalid IATA airport code: MADRID') })

    await expect(getJson('/api/itineraries?origin=MADRID')).rejects.toMatchObject({
      name: 'ApiError', status: 400, detail: 'Invalid IATA airport code: MADRID',
    })
  })

  it('keeps the running run id of a 409 on start', async () => {
    installFetch({ 'POST /api/ingestion/runs': () => problem(409, 'An ingestion run is already in progress', { runningRunId: RUN_A }) })

    const error = await startRun().catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).runningRunId).toBe(RUN_A)
    expect(errorMessage(error)).toBe('An ingestion run is already in progress')
  })

  it('falls back to the status line when the body is not a problem detail', async () => {
    installFetch({ 'GET /api/x': () => new Response('<html>bad gateway</html>', { status: 502, statusText: 'Bad Gateway' }) })

    await expect(getJson('/api/x')).rejects.toMatchObject({ status: 502, detail: '502 Bad Gateway' })
  })

  it('reads a 404 on the latest run as "no run yet"', async () => {
    installFetch({ 'GET /api/ingestion/runs/latest': () => problem(404, 'Not Found') })

    await expect(fetchLatestRun()).resolves.toBeNull()
  })
})
