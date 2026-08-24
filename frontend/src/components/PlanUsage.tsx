import { useState, useEffect } from 'react'
import { getRateLimitInfo, onRateLimitChange, type RateLimitInfo } from '../api/rateLimit'
import './PlanUsage.css'

/**
 * Sidebar widget showing API rate limit usage.
 * Subscribes to the rateLimit store for live updates.
 */
export default function PlanUsage() {
  const [info, setInfo] = useState<RateLimitInfo | null>(getRateLimitInfo)

  useEffect(() => {
    return onRateLimitChange(setInfo)
  }, [])

  if (!info) return null

  const used = info.burstCapacity - info.remaining
  const pct = Math.round((used / info.burstCapacity) * 100)
  const level = pct >= 90 ? 'critical' : pct >= 70 ? 'warning' : 'normal'

  return (
    <div className="plan-usage" aria-label="API usage">
      <div className="plan-usage-header">
        <span className="plan-usage-label">API Usage</span>
        <span className="plan-usage-count">{used}/{info.burstCapacity}</span>
      </div>
      <div className="plan-usage-track">
        <div
          className={`plan-usage-bar plan-usage-${level}`}
          style={{ width: `${Math.min(pct, 100)}%` }}
          role="progressbar"
          aria-valuenow={used}
          aria-valuemin={0}
          aria-valuemax={info.burstCapacity}
        />
      </div>
      <span className="plan-usage-rate">{info.replenishRate} req/s refill</span>
    </div>
  )
}
