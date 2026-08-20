/**
 * TypeScript interfaces mirroring the Order Service backend DTOs.
 *
 * These must stay in sync with:
 * - OrderResponse.java
 * - OrderItemResponse.java
 * - OrderStatus.java
 * - CreateOrderRequest.java
 * - OrderItemRequest.java
 */

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED'

export interface OrderItemResponse {
  id: string
  productId: string
  productName: string
  quantity: number
  unitPrice: number
}

export interface OrderResponse {
  id: string
  tenantId: string
  status: OrderStatus
  totalAmount: number
  items: OrderItemResponse[]
  createdAt: string
  updatedAt: string
}

export interface OrderItemRequest {
  productId: string
  quantity: number
}

export interface CreateOrderRequest {
  items: OrderItemRequest[]
}
