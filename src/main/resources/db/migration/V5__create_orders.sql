CREATE TABLE orders (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL,

    CONSTRAINT chk_orders_status
        CHECK (
            status IN (
                       'PENDING_STOCK',
                       'PENDING_PAYMENT',
                       'CONFIRMED',
                       'CANCELLED'
                )
            )
);

CREATE TABLE order_items (
     id UUID PRIMARY KEY,
     order_id UUID NOT NULL,
     item_position INTEGER NOT NULL,
     product_id UUID NOT NULL,
     product_name VARCHAR(150) NOT NULL,
     quantity INTEGER NOT NULL,
     unit_price NUMERIC(19, 2) NOT NULL,

     CONSTRAINT fk_order_items_order
         FOREIGN KEY (order_id)
             REFERENCES orders (id),

     CONSTRAINT uq_order_items_product
         UNIQUE (order_id, product_id),

     CONSTRAINT uq_order_items_position
         UNIQUE (order_id, item_position),

     CONSTRAINT chk_order_items_position
         CHECK (item_position >= 0),

     CONSTRAINT chk_order_items_name
         CHECK (CHAR_LENGTH(TRIM(product_name)) > 0),

     CONSTRAINT chk_order_items_quantity
         CHECK (quantity > 0),

     CONSTRAINT chk_order_items_price
         CHECK (unit_price > 0)
);