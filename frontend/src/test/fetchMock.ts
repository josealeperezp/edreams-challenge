import { vi, type Mock } from 'vitest'

export type RouteHandler = (url: URL, init?: RequestInit) => unknown
/** Keys are "METHOD /path/prefix"; the longest matching prefix wins. */
export type Routes = Record<string, RouteHandler>
export type FetchMock = Mock<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** An RFC 9457 problem detail as the backend's ApiExceptionHandler writes it. */
export function problem(status: number, detail: string, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ status, detail, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

export function installFetch(routes: Routes): FetchMock {
  const mock: FetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const raw = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url
    const url = new URL(raw, 'http://localhost')
    const method = (init?.method ?? 'GET').toUpperCase()
    const key = Object.keys(routes)
      .filter((candidate) => {
        const [m, path] = candidate.split(' ')
        return m === method && url.pathname.startsWith(path)
      })
      .sort((a, b) => b.length - a.length)[0]
    if (!key) return problem(404, `no route for ${method} ${url.pathname}`)
    const result = await routes[key](url, init)
    return result instanceof Response ? result : jsonResponse(result)
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

/** The URLs requested so far whose path starts with the prefix. */
export function requested(mock: FetchMock, prefix: string): URL[] {
  return mock.mock.calls
    .map(([input]) => new URL(typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url, 'http://localhost'))
    .filter((url) => url.pathname.startsWith(prefix))
}
