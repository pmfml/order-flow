-- =====================================================
-- V1: Order domain tables and Transactional Outbox
-- =====================================================

CREATE TABLE orders (
    id              UUID            PRIMARY KEY,
    tenant_id       VARCHAR(50)     NOT NULL,
    status          VARCHAR(30)     NOT NULL,
    total_amount    NUMERIC(19,2)   NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE order_items (
    id              UUID            PRIMARY KEY,
    order_id        UUID            NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      VARCHAR(50)     NOT NULL,
    product_name    VARCHAR(255)    NOT NULL,
    quantity        INT             NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(19,2)   NOT NULL
);

CREATE TABLE outbox_events (
    id              UUID            PRIMARY KEY,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    UUID            NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ
);

-- Indexes for common query patterns
CREATE INDEX idx_orders_tenant  ON orders (tenant_id);
CREATE INDEX idx_orders_status  ON orders (status);
CREATE INDEX idx_order_items_order ON order_items (order_id);
CREATE INDEX idx_outbox_unprocessed ON outbox_events (created_at) WHERE processed_at IS NULL;
