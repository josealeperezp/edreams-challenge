import { vi } from 'vitest'

type Listener = (event: MessageEvent<string>) => void

/** Stand-in for the browser's EventSource: tests push named events and failures by hand. */
export class FakeEventSource {
  static instances: FakeEventSource[] = []
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSED = 2

  readonly url: string
  readyState = FakeEventSource.CONNECTING
  closed = false
  onopen: ((event: Event) => void) | null = null
  onerror: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent) => void) | null = null
  private readonly listeners = new Map<string, Set<Listener>>()

  constructor(url: string | URL) {
    this.url = String(url)
    FakeEventSource.instances.push(this)
  }

  addEventListener(type: string, listener: Listener): void {
    if (!this.listeners.has(type)) this.listeners.set(type, new Set())
    this.listeners.get(type)!.add(listener)
  }

  removeEventListener(type: string, listener: Listener): void {
    this.listeners.get(type)?.delete(listener)
  }

  close(): void {
    this.closed = true
    this.readyState = FakeEventSource.CLOSED
  }

  open(): void {
    this.readyState = FakeEventSource.OPEN
    this.onopen?.(new Event('open'))
  }

  emit(type: string, data: unknown): void {
    const event = new MessageEvent<string>(type, { data: JSON.stringify(data) })
    this.listeners.get(type)?.forEach((listener) => listener(event))
  }

  fail(): void {
    this.onerror?.(new Event('error'))
  }

  static install(): void {
    FakeEventSource.instances = []
    vi.stubGlobal('EventSource', FakeEventSource)
  }

  static latest(): FakeEventSource {
    const last = FakeEventSource.instances.at(-1)
    if (!last) throw new Error('no EventSource was created')
    return last
  }
}
