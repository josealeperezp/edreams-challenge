import { describe, expect, it } from 'vitest'
import { durationBetween, formatCount, formatDuration, formatLatency, formatMoney, formatRelative, formatWallClock } from './format'

describe('format', () => {
  it('keeps the wall clock and offset the provider sent, without conversion', () => {
    expect(formatWallClock('2026-11-02T07:15:00+01:00')).toBe('02 Nov 07:15 +01:00')
    expect(formatWallClock('2026-11-02T06:15:00Z')).toBe('02 Nov 06:15 UTC')
    expect(formatWallClock('2026-11-02T06:15:00.123-05:00')).toBe('02 Nov 06:15 -05:00')
    expect(formatWallClock('garbage')).toBe('garbage')
  })

  it('formats money, counts, durations and latencies', () => {
    expect(formatMoney(89.78)).toBe('€89.78')
    expect(formatMoney(1200)).toBe('€1,200.00')
    expect(formatCount(954057)).toBe('954,057')
    expect(formatDuration(80 * 60_000)).toBe('1h 20m')
    expect(formatDuration(45 * 60_000)).toBe('45m')
    expect(formatDuration(26 * 3_600_000)).toBe('1d 2h')
    expect(formatDuration(104_000)).toBe('1m 44s')
    expect(durationBetween('2026-10-17T10:00:00Z', '2026-10-17T10:00:13Z')).toBe('13s')
    expect(formatLatency(850)).toBe('850 ms')
    expect(formatLatency(4321)).toBe('4.3 s')
  })

  it('describes relative times in both directions', () => {
    const now = Date.parse('2026-10-17T12:00:00Z')
    expect(formatRelative('2026-10-17T11:59:50Z', now)).toBe('just now')
    expect(formatRelative('2026-10-17T11:45:00Z', now)).toBe('15 min ago')
    expect(formatRelative('2026-10-17T09:00:00Z', now)).toBe('3 h ago')
    expect(formatRelative('2026-10-15T12:00:00Z', now)).toBe('2 d ago')
    expect(formatRelative('2026-10-17T12:30:00Z', now)).toBe('in 30 min')
  })
})
