CREATE TABLE stock_reservations (
    order_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    rejection_reason TEXT,

    CONSTRAINT chk_stock_reservations_status
        CHECK (
            status IN (
                       'RESERVED',
                       'RELEASED',
                       'CONFIRMED',
                       'REJECTED'
                )
            ),

    CONSTRAINT chk_stock_reservations_rejection_reason
        CHECK (
            (
                status = 'REJECTED'
                    AND rejection_reason IS NOT NULL
                    AND CHAR_LENGTH(TRIM(rejection_reason)) > 0
                )
                OR
            (
                status <> 'REJECTED'
                    AND rejection_reason IS NULL
                )
            )
);

CREATE TABLE stock_reservation_items (
     id UUID PRIMARY KEY,
     order_id UUID NOT NULL,
     item_position INTEGER NOT NULL,
     product_id UUID NOT NULL,
     quantity INTEGER NOT NULL,

     CONSTRAINT fk_stock_reservation_items_reservation
         FOREIGN KEY (order_id)
             REFERENCES stock_reservations (order_id),

     CONSTRAINT uq_stock_reservation_items_product
         UNIQUE (order_id, product_id),

     CONSTRAINT uq_stock_reservation_items_position
         UNIQUE (order_id, item_position),

     CONSTRAINT chk_stock_reservation_items_position
         CHECK (item_position >= 0),

     CONSTRAINT chk_stock_reservation_items_quantity
         CHECK (quantity > 0)
);