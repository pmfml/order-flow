import { useParams, Link } from 'react-router-dom'

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>()

  return (
    <div className="animate-fade-in">
      <div className="page-header">
        <Link to="/orders" className="btn btn-ghost" id="back-to-orders">← Back to Orders</Link>
        <h1 style={{ marginTop: 'var(--space-4)' }}>Order Detail</h1>
        <p className="text-secondary font-mono">{id}</p>
      </div>

      <div className="glass-card">
        <p className="text-muted">Saga timeline coming in Step 6</p>
      </div>
    </div>
  )
}
