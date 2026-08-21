import type { OrderStatus } from '../types/order'
import './SagaTimeline.css'

/**
 * Saga stages in the happy path order.
 * The timeline infers progress from the final order status because
 * the backend does not yet expose per-event audit data.
 */
const STAGES = [
  { key: 'created', label: 'Created' },
  { key: 'inventory', label: 'Inventory Reserved' },
  { key: 'payment', label: 'Payment Authorized' },
  { key: 'confirmed', label: 'Confirmed' },
] as const

type StageState = 'completed' | 'active' | 'pending' | 'failed'

interface SagaTimelineProps {
  status: OrderStatus
}

/**
 * Horizontal saga pipeline visualization.
 *
 * Derives each stage's visual state from the order's final status:
 * - PENDING   → "Created" is completed, rest are pending (saga in progress)
 * - CONFIRMED → all stages completed
 * - CANCELLED → "Created" completed, failure shown at the next stage
 */
export default function SagaTimeline({ status }: SagaTimelineProps) {
  const stages = resolveStages(status)

  return (
    <div className="saga-timeline" role="list" aria-label="Saga pipeline stages">
      {stages.map((stage, i) => (
        <div key={stage.key} className="saga-stage-wrapper" role="listitem">
          {i > 0 && (
            <div className={`saga-connector saga-connector-${stage.state}`} />
          )}
          <div className={`saga-node saga-node-${stage.state}`}>
            <div className="saga-node-dot">
              {stage.state === 'completed' && <span aria-hidden="true">✓</span>}
              {stage.state === 'active' && <span className="saga-pulse" aria-hidden="true" />}
              {stage.state === 'failed' && <span aria-hidden="true">✕</span>}
            </div>
            <span className="saga-node-label">{stage.label}</span>
          </div>
        </div>
      ))}

      {status === 'CANCELLED' && (
        <div className="saga-stage-wrapper" role="listitem">
          <div className="saga-connector saga-connector-failed" />
          <div className="saga-node saga-node-failed">
            <div className="saga-node-dot">
              <span aria-hidden="true">✕</span>
            </div>
            <span className="saga-node-label">Cancelled</span>
          </div>
        </div>
      )}
    </div>
  )
}

interface ResolvedStage {
  key: string
  label: string
  state: StageState
}

function resolveStages(status: OrderStatus): ResolvedStage[] {
  if (status === 'CONFIRMED') {
    return STAGES.map((s) => ({ ...s, state: 'completed' as StageState }))
  }

  if (status === 'CANCELLED') {
    // Created succeeded, next stage failed — show only "Created" as completed
    return STAGES.map((s, i) => ({
      ...s,
      state: (i === 0 ? 'completed' : 'pending') as StageState,
    }))
  }

  // PENDING — saga in progress: "Created" completed, next is active, rest pending
  return STAGES.map((s, i) => ({
    ...s,
    state: (i === 0 ? 'completed' : i === 1 ? 'active' : 'pending') as StageState,
  }))
}
