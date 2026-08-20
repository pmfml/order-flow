import { apiFetch } from './client'
import type { OrderResponse, CreateOrderRequest } from '../types/order'

const BASE = '/api/v1/orders'

/** Lists all orders for the authenticated tenant (most recent first). */
export function getOrders(): Promise<OrderResponse[]> {
  return apiFetch<OrderResponse[]>(BASE)
}

/** Fetches a single order by ID. */
export function getOrder(id: string): Promise<OrderResponse> {
  return apiFetch<OrderResponse>(`${BASE}/${id}`)
}

/** Creates a new order and returns the saved snapshot. */
export function createOrder(payload: CreateOrderRequest): Promise<OrderResponse> {
  return apiFetch<OrderResponse>(BASE, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
