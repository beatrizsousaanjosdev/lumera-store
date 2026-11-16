ALTER TABLE orders
DROP CONSTRAINT chk_orders_status;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_status
        CHECK (
            status IN (
                       'PENDING_STOCK',
                       'PENDING_PAYMENT',
                       'PENDING_STOCK_CONFIRMATION',
                       'PENDING_STOCK_RELEASE',
                       'CONFIRMED',
                       'CANCELLED'
                )
            );