import { useState } from 'react'
import { Link, useNavigate, Navigate, useSearchParams } from 'react-router-dom'
import { useCart } from '../../store/CartContext'
import { createOrder } from '../../api/orders'
import './CheckoutPage.css'

export default function CheckoutPage() {
  const { items, totalPrice, clearCart } = useCart()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const successOrderId = searchParams.get('success')

  if (successOrderId) {
    return (
      <div className="checkout-success">
        <div className="success-icon">🎉</div>
        <h1>Order Confirmed!</h1>
        <p>Your order <strong>{successOrderId}</strong> has been successfully placed.</p>
        <p className="saga-note">
          OrderFlow's Choreographed Saga is now processing your request (inventory reservation and payment). 
        </p>
        <div className="success-actions">
          <Link to={`/orders/${successOrderId}`} className="btn-primary">
            Track Saga Progress in Admin
          </Link>
          <Link to="/store" className="btn-secondary">
            Continue Shopping
          </Link>
        </div>
      </div>
    )
  }

  if (items.length === 0) {
    return <Navigate to="/store" replace />
  }

  const handleCheckout = async (e: React.FormEvent) => {
    e.preventDefault()
    
    try {
      setSubmitting(true)
      setError(null)
      
      const payload = {
        items: items.map(item => ({
          productId: item.product.id,
          quantity: item.quantity
        }))
      }
      
      const order = await createOrder(payload)
      clearCart()
      navigate(`/store/checkout?success=${order.id}`, { replace: true })
      
    } catch (err: any) {
      setError(err.message || 'An error occurred during checkout.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="checkout-page">
      <div className="checkout-header">
        <h1>Checkout</h1>
        <Link to="/store/cart" className="back-link">← Back to Cart</Link>
      </div>
      
      <div className="checkout-container">
        <form className="checkout-form" onSubmit={handleCheckout}>
          <div className="form-section">
            <h2>Payment Information</h2>
            <p className="demo-note">This is a B2C demo environment. No real payment is required.</p>
            
            <div className="form-group">
              <label>Name on Card</label>
              <input type="text" defaultValue="John Doe" required />
            </div>
            
            <div className="form-group">
              <label>Card Number</label>
              <input type="text" defaultValue="•••• •••• •••• 4242" required />
            </div>
          </div>
          
          {error && <div className="error-message">{error}</div>}
          
          <button 
            type="submit" 
            className="btn-primary place-order-btn"
            disabled={submitting}
          >
            {submitting ? 'Processing...' : `Place Order • $${totalPrice.toFixed(2)}`}
          </button>
        </form>
        
        <div className="checkout-summary">
          <h2>Order Summary</h2>
          <div className="summary-items">
            {items.map(item => (
              <div key={item.product.id} className="summary-item">
                <span className="summary-item-name">{item.quantity}x {item.product.name}</span>
                <span className="summary-item-price">${(item.product.price * item.quantity).toFixed(2)}</span>
              </div>
            ))}
          </div>
          <div className="summary-total">
            <span>Total</span>
            <span>${totalPrice.toFixed(2)}</span>
          </div>
        </div>
      </div>
    </div>
  )
}
