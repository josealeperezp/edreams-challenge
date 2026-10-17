import { useEffect, useRef, useState, type RefObject } from 'react'

/** Measured width of an element, so charts get explicit pixel sizes (no ResponsiveContainer, testable in jsdom). */
export function useElementWidth<T extends HTMLElement>(fallback = 600): [RefObject<T | null>, number] {
  const ref = useRef<T>(null)
  const [width, setWidth] = useState(fallback)

  useEffect(() => {
    const element = ref.current
    if (!element || typeof ResizeObserver === 'undefined') return
    const update = () => {
      const measured = Math.floor(element.getBoundingClientRect().width)
      if (measured > 0) setWidth(measured)
    }
    update()
    const observer = new ResizeObserver(update)
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  return [ref, width]
}
