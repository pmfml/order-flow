import { useState, useEffect } from 'react'
import { getOrders } from '../api/orders'
import type { OrderResponse } from '../types/order'
import './OrdersPage.css'

export default function OrdersPage() {
  const [orders, setOrders] = useState<OrderResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function fetchOrders() {
      try {
        const data = await getOrders()
        if (!cancelled) {
          setOrders(data)
          setError(null)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load orders')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    fetchOrders()
    return () => { cancelled = true }
  }, [])

  return (
    <div className="animate-fade-in">
      <div className="page-header">
        <h1>Orders</h1>
        <p className="text-secondary">Monitor your orders in real time</p>
      </div>

      {loading && <LoadingSkeleton />}

      {error && (
        <div className="glass-card orders-error">
          <p>⚠️ {error}</p>
          <p className="text-muted">Make sure the backend is running on port 8090.</p>
        </div>
      )}

      {!loading && !error && orders.length === 0 && <EmptyState />}

      {!loading && !error && orders.length > 0 && (
        <div className="glass-card orders-table-wrapper">
          <table className="orders-table" id="orders-table">
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Status</th>
                <th>Total</th>
                <th>Items</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody className="stagger-enter">
              {orders.map((order) => (
                <tr key={order.id} className="orders-row">
                  <td className="font-mono truncate">{order.id.slice(0, 8)}…</td>
                  <td>{order.status}</td>
                  <td>${order.totalAmount.toFixed(2)}</td>
                  <td>{order.items.length}</td>
                  <td className="text-secondary">{new Date(order.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function LoadingSkeleton() {
  return (
    <div className="glass-card">
      <div className="skeleton-row animate-pulse" />
      <div className="skeleton-row animate-pulse" />
      <div className="skeleton-row animate-pulse" />
    </div>
  )
}

function EmptyState() {
  return (
    <div className="glass-card orders-empty">
      <span className="orders-empty-icon" aria-hidden="true">📦</span>
      <h3>No orders yet</h3>
      <p className="text-secondary">Create your first order to get started.</p>
    </div>
  )
}
