/**
 * TypeScript interfaces mirroring the Inventory Service backend DTOs.
 *
 * These must stay in sync with:
 * - ProductResponse.java (inventory-service)
 * - Product.java (MongoDB document)
 */

export type ProductCategory = 'ELECTRONICS' | 'CLOTHING' | 'HOME' | 'SPORTS' | 'OTHER' | string

export type StockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK'

export interface Product {
  id: string
  sku: string
  name: string
  category: ProductCategory
  price: number
  stockQuantity: number
  attributes: Record<string, string>
  createdAt: string
  updatedAt: string
}

/** Client-side cart item — enriched product snapshot with chosen quantity. */
export interface CartItem {
  product: Product
  quantity: number
}

/** Helper function to derive visual stock status from available quantity */
export function getStockStatus(quantity: number): StockStatus {
  if (quantity <= 0) return 'OUT_OF_STOCK'
  if (quantity <= 5) return 'LOW_STOCK'
  return 'IN_STOCK'
}
