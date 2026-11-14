ALTER TABLE outbox_events
    RENAME COLUMN product_id TO aggregate_id;

ALTER TABLE outbox_events
    ADD COLUMN aggregate_type VARCHAR(50);

UPDATE outbox_events
SET aggregate_type = 'Product';

ALTER TABLE outbox_events
    ALTER COLUMN aggregate_type SET NOT NULL;

ALTER TABLE outbox_events
    ADD CONSTRAINT chk_outbox_events_aggregate_type
        CHECK (CHAR_LENGTH(TRIM(aggregate_type)) > 0);