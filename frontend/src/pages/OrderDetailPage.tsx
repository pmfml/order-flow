import { useState, useEffect } from 'react'
import { useParams, Link } from 'react-router-dom'
import { getOrder } from '../api/orders'
import type { OrderResponse } from '../types/order'
import StatusBadge from '../components/StatusBadge'
import SagaTimeline from '../components/SagaTimeline'
import './OrderDetailPage.css'

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [order, setOrder] = useState<OrderResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return

    async function fetchOrder() {
      try {
        const data = await getOrder(id!)
        setOrder(data)
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load order')
      } finally {
        setLoading(false)
      }
    }

    fetchOrder()
  }, [id])

  if (loading) {
    return (
      <div className="animate-fade-in">
        <div className="glass-card detail-loading">
          <div className="skeleton-row animate-pulse" />
          <div className="skeleton-row animate-pulse" />
        </div>
      </div>
    )
  }

  if (error || !order) {
    return (
      <div className="animate-fade-in">
        <Link to="/orders" className="btn btn-ghost" id="back-to-orders">← Back to Orders</Link>
        <div className="glass-card detail-error">
          <p>⚠️ {error ?? 'Order not found'}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="animate-fade-in">
      <div className="page-header">
        <Link to="/orders" className="btn btn-ghost" id="back-to-orders">← Back to Orders</Link>
        <div className="detail-title-row">
          <h1>Order Detail</h1>
          <StatusBadge status={order.status} />
        </div>
        <p className="font-mono text-secondary">{order.id}</p>
      </div>

      <section className="glass-card detail-section" aria-label="Saga pipeline">
        <h3>Saga Pipeline</h3>
        <SagaTimeline status={order.status} />
      </section>

      <section className="glass-card detail-section" aria-label="Order summary">
        <h3>Summary</h3>
        <dl className="detail-grid">
          <div className="detail-field">
            <dt>Total Amount</dt>
            <dd className="detail-total">${order.totalAmount.toFixed(2)}</dd>
          </div>
          <div className="detail-field">
            <dt>Created</dt>
            <dd>{new Date(order.createdAt).toLocaleString()}</dd>
          </div>
          <div className="detail-field">
            <dt>Updated</dt>
            <dd>{new Date(order.updatedAt).toLocaleString()}</dd>
          </div>
          <div className="detail-field">
            <dt>Tenant</dt>
            <dd className="font-mono">{order.tenantId}</dd>
          </div>
        </dl>
      </section>

      {order.items.length > 0 && (
        <section className="glass-card detail-section" aria-label="Line items">
          <h3>Items ({order.items.length})</h3>
          <table className="detail-items-table">
            <thead>
              <tr>
                <th>Product</th>
                <th>Qty</th>
                <th>Unit Price</th>
                <th>Subtotal</th>
              </tr>
            </thead>
            <tbody>
              {order.items.map((item) => (
                <tr key={item.id}>
                  <td>{item.productName}</td>
                  <td>{item.quantity}</td>
                  <td>${item.unitPrice.toFixed(2)}</td>
                  <td>${(item.unitPrice * item.quantity).toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  )
}
