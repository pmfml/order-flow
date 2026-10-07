import { Link } from 'react-router-dom'
import { useCart } from '../../store/CartContext'
import './CartPage.css'

export default function CartPage() {
  const { items, updateQuantity, removeItem, totalPrice } = useCart()

  if (items.length === 0) {
    return (
      <div className="cart-page empty">
        <span className="empty-icon">🛒</span>
        <h2>Your cart is empty</h2>
        <p>Looks like you haven't added any products yet.</p>
        <Link to="/store" className="btn-primary">Browse Catalog</Link>
      </div>
    )
  }

  return (
    <div className="cart-page">
      <h1>Shopping Cart</h1>
      <div className="cart-container">
        <div className="cart-items">
          {items.map((item) => (
            <div key={item.product.id} className="cart-item">
              <div className="cart-item-details">
                <h3>{item.product.name}</h3>
                <p className="cart-item-category">{item.product.category}</p>
                <div className="cart-item-price">${item.product.price.toFixed(2)}</div>
              </div>
              <div className="cart-item-actions">
                <div className="quantity-controls">
                  <button 
                    onClick={() => updateQuantity(item.product.id, item.quantity - 1)}
                    disabled={item.quantity <= 1}
                  >-</button>
                  <span>{item.quantity}</span>
                  <button 
                    onClick={() => updateQuantity(item.product.id, item.quantity + 1)}
                    disabled={item.quantity >= item.product.stockQuantity}
                  >+</button>
                </div>
                <button 
                  className="btn-remove"
                  onClick={() => removeItem(item.product.id)}
                >
                  Remove
                </button>
              </div>
            </div>
          ))}
        </div>
        <div className="cart-summary">
          <h2>Summary</h2>
          <div className="summary-row">
            <span>Subtotal</span>
            <span>${totalPrice.toFixed(2)}</span>
          </div>
          <div className="summary-row">
            <span>Taxes (Estimated)</span>
            <span>$0.00</span>
          </div>
          <div className="summary-row total">
            <span>Total</span>
            <span>${totalPrice.toFixed(2)}</span>
          </div>
          <Link to="/store/checkout" className="btn-primary checkout-btn">
            Proceed to Checkout
          </Link>
        </div>
      </div>
    </div>
  )
}
