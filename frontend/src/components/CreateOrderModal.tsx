import { useState, type FormEvent } from 'react'
import { createOrder } from '../api/orders'
import type { OrderItemRequest } from '../types/order'
import './CreateOrderModal.css'

interface CreateOrderModalProps {
  onClose: () => void
  onCreated: () => void
}

export default function CreateOrderModal({ onClose, onCreated }: CreateOrderModalProps) {
  const [items, setItems] = useState<OrderItemRequest[]>([
    { productId: '', quantity: 1 },
  ])
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function addItem() {
    setItems([...items, { productId: '', quantity: 1 }])
  }

  function removeItem(index: number) {
    if (items.length <= 1) return
    setItems(items.filter((_, i) => i !== index))
  }

  function updateItem(index: number, field: keyof OrderItemRequest, value: string | number) {
    setItems(items.map((item, i) =>
      i === index ? { ...item, [field]: value } : item
    ))
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)

    // Validation
    const valid = items.every((item) => item.productId.trim() && item.quantity >= 1)
    if (!valid) {
      setError('Each item needs a Product ID and quantity ≥ 1.')
      return
    }

    setSubmitting(true)
    try {
      await createOrder({ items })
      onCreated()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create order')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <form
        className="modal-card glass-card animate-fade-in"
        onClick={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
      >
        <div className="modal-header">
          <h2>New Order</h2>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close">✕</button>
        </div>

        <div className="modal-body">
          {items.map((item, i) => (
            <div key={i} className="modal-item-row">
              <input
                className="input"
                type="text"
                placeholder="Product ID"
                value={item.productId}
                onChange={(e) => updateItem(i, 'productId', e.target.value)}
                required
              />
              <input
                className="input modal-qty-input"
                type="number"
                min={1}
                placeholder="Qty"
                value={item.quantity}
                onChange={(e) => updateItem(i, 'quantity', parseInt(e.target.value) || 1)}
                required
              />
              {items.length > 1 && (
                <button type="button" className="btn btn-ghost modal-remove-btn" onClick={() => removeItem(i)}>
                  ✕
                </button>
              )}
            </div>
          ))}

          <button type="button" className="btn btn-ghost modal-add-btn" onClick={addItem}>
            + Add Item
          </button>
        </div>

        {error && <p className="modal-error">{error}</p>}

        <div className="modal-footer">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" id="submit-order" disabled={submitting}>
            {submitting ? 'Creating…' : 'Create Order'}
          </button>
        </div>
      </form>
    </div>
  )
}
