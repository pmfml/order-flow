import type { OrderStatus } from '../types/order'

const CONFIG: Record<OrderStatus, string> = {
  PENDING: 'badge-pending',
  CONFIRMED: 'badge-confirmed',
  CANCELLED: 'badge-cancelled',
}

interface StatusBadgeProps {
  status: OrderStatus
}

/** Color-coded status badge using design system tokens. */
export default function StatusBadge({ status }: StatusBadgeProps) {
  return (
    <span className={`badge ${CONFIG[status]}`}>
      {status}
    </span>
  )
}
