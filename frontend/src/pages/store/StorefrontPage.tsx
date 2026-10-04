import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getProducts } from '../../api/products'
import type { Product } from '../../types/product'
import ProductCard from '../../components/ProductCard'
import './StorefrontPage.css'

export default function StorefrontPage() {
  const [products, setProducts] = useState<Product[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  
  const [searchParams, setSearchParams] = useSearchParams()
  const categoryFilter = searchParams.get('category') || ''

  useEffect(() => {
    const fetchProducts = async () => {
      try {
        setLoading(true)
        const data = await getProducts({ category: categoryFilter })
        setProducts(data)
        setError(null)
      } catch (err: any) {
        setError(err.message || 'Failed to load products.')
      } finally {
        setLoading(false)
      }
    }

    fetchProducts()
  }, [categoryFilter])

  const categories = ['ALL', 'ELECTRONICS', 'CLOTHING', 'HOME', 'SPORTS', 'OTHER']

  return (
    <div className="storefront-page">
      <div className="storefront-header-section">
        <h1>Catalog</h1>
        <div className="category-filters">
          {categories.map((cat) => (
            <button
              key={cat}
              className={`filter-btn ${
                (cat === 'ALL' && !categoryFilter) || categoryFilter === cat ? 'active' : ''
              }`}
              onClick={() => {
                const newParams = new URLSearchParams(searchParams)
                if (cat === 'ALL') {
                  newParams.delete('category')
                } else {
                  newParams.set('category', cat)
                }
                setSearchParams(newParams)
              }}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      {loading ? (
        <div className="product-grid">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <div key={i} className="skeleton-card"></div>
          ))}
        </div>
      ) : products.length === 0 ? (
        <div className="empty-state">
          <span className="empty-icon">📭</span>
          <p>No products found.</p>
        </div>
      ) : (
        <div className="product-grid">
          {products.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      )}
    </div>
  )
}
