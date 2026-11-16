CREATE TABLE order_notifications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    order_id UUID NOT NULL UNIQUE,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('CONFIRMED', 'CANCELLED')),
    processed_at TIMESTAMPTZ NOT NULL
);
