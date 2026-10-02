import { Link, Outlet } from 'react-router-dom'
import { useCart } from '../store/CartContext'
import './StorefrontLayout.css'

export default function StorefrontLayout() {
  const { totalItems } = useCart()

  return (
    <div className="storefront-layout">
      <header className="storefront-header">
        <div className="storefront-logo">
          <Link to="/store">
            <span aria-hidden="true" className="logo-icon">⚡</span>
            OrderFlow Store
          </Link>
        </div>
        <div className="storefront-search">
          <input type="text" placeholder="Search products..." />
        </div>
        <nav className="storefront-nav">
          <Link to="/store/cart" className="cart-badge-link">
            🛒 Cart {totalItems > 0 && <span className="cart-badge">{totalItems}</span>}
          </Link>
          <Link to="/orders" className="admin-link">
            ⚙️ Admin Dashboard
          </Link>
        </nav>
      </header>
      <main className="storefront-main">
        <Outlet />
      </main>
      <footer className="storefront-footer">
        <p>&copy; 2026 OrderFlow. Simulated B2C Environment.</p>
      </footer>
    </div>
  )
}
