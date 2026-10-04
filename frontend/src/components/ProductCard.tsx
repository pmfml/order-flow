import { type Product, getStockStatus } from '../types/product'
import { useCart } from '../store/CartContext'
import './ProductCard.css'
import { useState } from 'react'

interface ProductCardProps {
  product: Product
}

export default function ProductCard({ product }: ProductCardProps) {
  const { addItem } = useCart()
  const status = getStockStatus(product.stockQuantity)
  const [added, setAdded] = useState(false)

  const handleAdd = () => {
    addItem(product, 1)
    setAdded(true)
    setTimeout(() => setAdded(false), 2000)
  }

  const isOutOfStock = status === 'OUT_OF_STOCK'

  return (
    <div className={`product-card ${isOutOfStock ? 'out-of-stock' : ''}`}>
      <div className="product-image-placeholder">
        {status === 'LOW_STOCK' && <span className="badge badge-warning">Low Stock</span>}
        {status === 'OUT_OF_STOCK' && <span className="badge badge-danger">Out of Stock</span>}
        <span className="product-category">{product.category}</span>
      </div>
      <div className="product-info">
        <h3>{product.name}</h3>
        <p className="product-sku">SKU: {product.sku}</p>
        <div className="product-price">
          ${product.price.toFixed(2)}
        </div>
        <button 
          className={`add-to-cart-btn ${added ? 'added' : ''}`}
          onClick={handleAdd}
          disabled={isOutOfStock}
        >
          {added ? '✓ Added' : isOutOfStock ? 'Unavailable' : 'Add to Cart'}
        </button>
      </div>
    </div>
  )
}
