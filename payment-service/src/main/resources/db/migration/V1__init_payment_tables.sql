CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    order_id UUID NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    stripe_payment_intent_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_payment_transactions_order_tenant ON payment_transactions (order_id, tenant_id);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
