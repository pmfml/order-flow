import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { getOrders } from '../api/orders'
import type { OrderResponse } from '../types/order'
import StatusBadge from '../components/StatusBadge'
import './OrdersPage.css'

const POLL_INTERVAL_MS = 5_000

export default function OrdersPage() {
  const [orders, setOrders] = useState<OrderResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()

  const fetchOrders = useCallback(async (isInitial: boolean) => {
    try {
      const data = await getOrders()
      setOrders(data)
      setError(null)
    } catch (err) {
      if (isInitial) {
        setError(err instanceof Error ? err.message : 'Failed to load orders')
      }
    } finally {
      if (isInitial) setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchOrders(true)
    const interval = setInterval(() => fetchOrders(false), POLL_INTERVAL_MS)
    return () => clearInterval(interval)
  }, [fetchOrders])

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
                <tr
                  key={order.id}
                  className="orders-row orders-row-clickable"
                  onClick={() => navigate(`/orders/${order.id}`)}
                  role="link"
                  tabIndex={0}
                  onKeyDown={(e) => e.key === 'Enter' && navigate(`/orders/${order.id}`)}
                >
                  <td className="font-mono truncate">{order.id.slice(0, 8)}…</td>
                  <td><StatusBadge status={order.status} /></td>
                  <td>${order.totalAmount.toFixed(2)}</td>
                  <td>{order.items.length}</td>
                  <td className="text-secondary">{timeAgo(order.createdAt)}</td>
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

/** Converts an ISO timestamp to a human-friendly relative string. */
function timeAgo(iso: string): string {
  const seconds = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (seconds < 60) return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  return `${days}d ago`
}

