-- =====================================================
-- V3: Idempotent consumer deduplication table
--
-- Every Kafka listener in Order Service checks this table before processing
-- a message. The event_id (from the canonical EventEnvelope) is inserted in
-- the same transaction as the business effect, so the two are committed
-- atomically. See EVENTS.md §3 for the full idempotency contract.
-- =====================================================

CREATE TABLE processed_events (
    event_id     UUID           PRIMARY KEY,
    event_type   VARCHAR(255)   NOT NULL,
    processed_at TIMESTAMPTZ    NOT NULL
);
