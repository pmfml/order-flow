import { apiFetch } from './client'
import type { Product } from '../types/product'

const BASE = '/api/v1/products'

export interface GetProductsParams {
  category?: string
  search?: string
}

/** Lists all products in the catalog for the authenticated tenant. */
export function getProducts(params?: GetProductsParams): Promise<Product[]> {
  const searchParams = new URLSearchParams()
  if (params?.category) searchParams.append('category', params.category)
  if (params?.search) searchParams.append('search', params.search)
  
  const queryString = searchParams.toString()
  const path = queryString ? `${BASE}?${queryString}` : BASE
  
  return apiFetch<Product[]>(path)
}

/** Fetches a single product by ID. */
export function getProduct(id: string): Promise<Product> {
  return apiFetch<Product>(`${BASE}/${id}`)
}
