import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { getProduct } from '../../api/products'
import type { Product } from '../../types/product'
import { getStockStatus } from '../../types/product'
import { useCart } from '../../store/CartContext'
import './ProductDetailPage.css'

export default function ProductDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { addItem } = useCart()
  
  const [product, setProduct] = useState<Product | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [added, setAdded] = useState(false)

  useEffect(() => {
    if (!id) return

    const fetchProduct = async () => {
      try {
        setLoading(true)
        const data = await getProduct(id)
        setProduct(data)
        setError(null)
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Failed to load product details.')
      } finally {
        setLoading(false)
      }
    }

    fetchProduct()
  }, [id])

  if (loading) {
    return (
      <div className="product-detail-page">
        <div className="detail-skeleton">
          <div className="skeleton-image"></div>
          <div className="skeleton-content">
            <div className="skeleton-line title"></div>
            <div className="skeleton-line"></div>
            <div className="skeleton-line price"></div>
            <div className="skeleton-line button"></div>
          </div>
        </div>
      </div>
    )
  }

  if (error || !product) {
    return (
      <div className="product-detail-page not-found">
        <span className="not-found-icon">⚠️</span>
        <h2>Product Not Found</h2>
        <p>{error || 'The requested product does not exist or has been removed from catalog.'}</p>
        <Link to="/store" className="btn-primary">
          ← Back to Catalog
        </Link>
      </div>
    )
  }

  const stockStatus = getStockStatus(product.stockQuantity)
  const isOutOfStock = stockStatus === 'OUT_OF_STOCK'
  const attributesEntries = Object.entries(product.attributes || {})

  const handleAddToCart = () => {
    if (isOutOfStock) return
    addItem(product, quantity)
    setAdded(true)
    setTimeout(() => setAdded(false), 2500)
  }

  const incrementQty = () => {
    if (quantity < product.stockQuantity) {
      setQuantity(q => q + 1)
    }
  }

  const decrementQty = () => {
    if (quantity > 1) {
      setQuantity(q => q - 1)
    }
  }

  return (
    <div className="product-detail-page">
      <nav className="detail-breadcrumb" aria-label="Breadcrumb">
        <Link to="/store">Catalog</Link>
        <span className="breadcrumb-separator">/</span>
        <span className="breadcrumb-category">{product.category}</span>
        <span className="breadcrumb-separator">/</span>
        <span className="breadcrumb-current">{product.name}</span>
      </nav>

      <div className="product-detail-container">
        {/* Left Column: Visual Showcase */}
        <div className="product-visual-showcase">
          <div className="visual-display">
            <span className="visual-category-badge">{product.category}</span>
            <div className="visual-icon-wrapper">
              <span className="visual-icon">📦</span>
            </div>
            {stockStatus === 'LOW_STOCK' && (
              <span className="detail-badge badge-warning">Low Stock ({product.stockQuantity} left)</span>
            )}
            {stockStatus === 'OUT_OF_STOCK' && (
              <span className="detail-badge badge-danger">Out of Stock</span>
            )}
          </div>
        </div>

        {/* Right Column: Information & Actions */}
        <div className="product-info-panel">
          <div className="product-header">
            <span className="sku-tag">SKU: {product.sku}</span>
            <h1 className="product-title">{product.name}</h1>
          </div>

          <div className="price-stock-row">
            <div className="product-price">${product.price.toFixed(2)}</div>
            <div className={`stock-indicator status-${stockStatus.toLowerCase()}`}>
              <span className="status-dot"></span>
              {stockStatus === 'IN_STOCK' && `In Stock (${product.stockQuantity} available)`}
              {stockStatus === 'LOW_STOCK' && `Only ${product.stockQuantity} units left in stock`}
              {stockStatus === 'OUT_OF_STOCK' && 'Currently Unavailable'}
            </div>
          </div>

          <p className="product-summary">
            Enterprise-grade fulfillment backed by OrderFlow's distributed choreographed Saga.
            Authoritative pricing and inventory guaranteed via gRPC synchronous pre-checks.
          </p>

          <div className="purchase-controls">
            <div className="quantity-picker">
              <label htmlFor="detail-qty" className="quantity-label">Quantity</label>
              <div className="quantity-stepper">
                <button
                  type="button"
                  onClick={decrementQty}
                  disabled={quantity <= 1 || isOutOfStock}
                  aria-label="Decrease quantity"
                >
                  -
                </button>
                <span id="detail-qty">{quantity}</span>
                <button
                  type="button"
                  onClick={incrementQty}
                  disabled={quantity >= product.stockQuantity || isOutOfStock}
                  aria-label="Increase quantity"
                >
                  +
                </button>
              </div>
            </div>

            <button
              type="button"
              className={`add-to-cart-action ${added ? 'added' : ''}`}
              onClick={handleAddToCart}
              disabled={isOutOfStock}
            >
              {added ? '✓ Added to Cart' : isOutOfStock ? 'Out of Stock' : `Add to Cart • $${(product.price * quantity).toFixed(2)}`}
            </button>
          </div>

          {added && (
            <div className="post-add-banner">
              <span>Item added to your shopping cart!</span>
              <div className="post-add-links">
                <Link to="/store/cart" className="view-cart-link">View Cart →</Link>
                <Link to="/store/checkout" className="direct-checkout-link">Checkout Now →</Link>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Specifications & Schema Flexibility Showcase */}
      <section className="product-specs-section">
        <div className="specs-header">
          <h2>Technical Specifications</h2>
          <span className="persistence-tag">🍃 MongoDB Flexible Schema</span>
        </div>
        
        <p className="specs-description">
          In OrderFlow, product catalog data is persisted in MongoDB to easily accommodate dynamic, category-specific 
          attributes without cumbersome database schema migrations or sparse SQL columns.
        </p>

        {attributesEntries.length > 0 ? (
          <div className="attributes-grid">
            {attributesEntries.map(([key, value]) => (
              <div key={key} className="attribute-card">
                <span className="attribute-key">{key.replace(/_/g, ' ')}</span>
                <span className="attribute-value">{String(value)}</span>
              </div>
            ))}
          </div>
        ) : (
          <div className="no-attributes">
            <p>No additional dynamic specifications configured for this item.</p>
          </div>
        )}
      </section>
    </div>
  )
}
