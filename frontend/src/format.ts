/** Display helpers. Times are shown as the provider sent them (wall clock plus offset), never converted. */
const WALL_CLOCK = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?(Z|[+-]\d{2}:\d{2})$/
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

const count = new Intl.NumberFormat('en')

export function formatCount(value: number): string {
  return count.format(value)
}

export function formatMoney(amount: number, currency = 'EUR'): string {
  return new Intl.NumberFormat('en', { style: 'currency', currency, minimumFractionDigits: 2 }).format(amount)
}

export function formatWallClock(iso: string): string {
  const match = WALL_CLOCK.exec(iso)
  if (!match) return iso
  const [, , month, day, hours, minutes, offset] = match
  const zone = offset === 'Z' ? 'UTC' : offset
  return `${day} ${MONTHS[Number(month) - 1]} ${hours}:${minutes} ${zone}`
}

/** Flight durations are whole minutes ("1h 10m"); run durations under an hour keep their seconds ("1m 44s", "13s"). */
export function formatDuration(millis: number): string {
  const totalSeconds = Math.max(0, Math.round(millis / 1000))
  const days = Math.floor(totalSeconds / 86_400)
  const hours = Math.floor((totalSeconds % 86_400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  if (days > 0) return `${days}d ${hours}h`
  if (hours > 0) return `${hours}h ${String(minutes).padStart(2, '0')}m`
  if (minutes > 0) return seconds > 0 ? `${minutes}m ${seconds}s` : `${minutes}m`
  return `${seconds}s`
}

export function durationBetween(fromIso: string, toIso: string): string {
  return formatDuration(Date.parse(toIso) - Date.parse(fromIso))
}

export function formatLatency(millis: number): string {
  return millis < 1000 ? `${millis} ms` : `${(millis / 1000).toFixed(1)} s`
}

export function formatRelative(iso: string, now: number = Date.now()): string {
  const seconds = Math.round((now - Date.parse(iso)) / 1000)
  const future = seconds < 0
  const s = Math.abs(seconds)
  let text: string
  if (s < 45) text = 'just now'
  else if (s < 3600) text = `${Math.round(s / 60)} min`
  else if (s < 86_400) text = `${Math.round(s / 3600)} h`
  else text = `${Math.round(s / 86_400)} d`
  if (text === 'just now') return text
  return future ? `in ${text}` : `${text} ago`
}
