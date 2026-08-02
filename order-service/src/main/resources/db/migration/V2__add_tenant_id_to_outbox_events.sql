-- =====================================================
-- V2: Carry tenant_id on outbox events
--
-- The event envelope (ARCHITECTURE.md §7.2) exposes tenantId at the root of
-- the message, not inside the payload. Storing it as a column keeps the outbox
-- row the single source of truth from which the envelope is derived at publish
-- time, so republishing the same row always produces an identical envelope.
-- =====================================================

-- Added nullable first so the migration is safe on databases that already
-- hold outbox rows from before this column existed.
ALTER TABLE outbox_events ADD COLUMN tenant_id VARCHAR(50);

-- Backfill from the owning aggregate. Every existing row is an Order event,
-- so aggregate_id resolves against orders.id.
UPDATE outbox_events oe
   SET tenant_id = o.tenant_id
  FROM orders o
 WHERE oe.aggregate_id = o.id
   AND oe.tenant_id IS NULL;

-- Fails loudly if any row could not be backfilled, which is preferable to
-- silently assigning events to a placeholder tenant.
ALTER TABLE outbox_events ALTER COLUMN tenant_id SET NOT NULL;
