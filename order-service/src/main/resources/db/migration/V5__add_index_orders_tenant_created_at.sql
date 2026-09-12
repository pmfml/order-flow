-- =====================================================
-- V5: Add composite index for dashboard query optimization
--
-- Optimizes the OrderRepository.findByTenantIdOrderByCreatedAtDesc query
-- which is heavily used to list the latest orders for a tenant.
-- =====================================================

CREATE INDEX idx_orders_tenant_created_at ON orders (tenant_id, created_at DESC);
