import { useEffect, useState } from 'react'

/** Current time, refreshed every second while active, so "running for 12 s" counts up. */
export function useTicker(active: boolean, periodMs = 1000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    setNow(Date.now())
    if (!active) return
    const timer = setInterval(() => setNow(Date.now()), periodMs)
    return () => clearInterval(timer)
  }, [active, periodMs])
  return now
}
