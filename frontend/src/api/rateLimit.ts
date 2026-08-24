/**
 * Module-level store for rate limit headers returned by the API Gateway.
 *
 * Updated as a side-effect of apiFetch() — components subscribe via
 * onChange() for reactivity without a state management library.
 */

export interface RateLimitInfo {
  remaining: number
  burstCapacity: number
  replenishRate: number
}

type Listener = (info: RateLimitInfo) => void

let current: RateLimitInfo | null = null
const listeners: Set<Listener> = new Set()

/** Returns the latest captured rate limit info, or null if none yet. */
export function getRateLimitInfo(): RateLimitInfo | null {
  return current
}

/** Subscribes to rate limit updates. Returns an unsubscribe function. */
export function onRateLimitChange(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** Called internally by apiFetch to capture headers from each response. */
export function captureRateLimitHeaders(headers: Headers): void {
  const remaining = headers.get('X-RateLimit-Remaining')
  const burst = headers.get('X-RateLimit-Burst-Capacity')
  const replenish = headers.get('X-RateLimit-Replenish-Rate')

  if (remaining === null || burst === null) return

  current = {
    remaining: parseInt(remaining, 10),
    burstCapacity: parseInt(burst, 10),
    replenishRate: replenish ? parseInt(replenish, 10) : 10,
  }

  listeners.forEach((fn) => fn(current!))
}
