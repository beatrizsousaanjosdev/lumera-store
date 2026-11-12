CREATE TABLE outbox_events (
   event_id UUID PRIMARY KEY,
   product_id UUID NOT NULL,
   event_type VARCHAR(100) NOT NULL,
   payload TEXT NOT NULL,
   occurred_at TIMESTAMPTZ NOT NULL,
   published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_pending
    ON outbox_events (occurred_at, event_id)
    WHERE published_at IS NULL;