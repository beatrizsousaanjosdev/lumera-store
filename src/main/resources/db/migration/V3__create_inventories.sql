CREATE TABLE inventories (
     product_id UUID PRIMARY KEY,
     quantity INTEGER NOT NULL,
     reserved_quantity INTEGER NOT NULL,

     CONSTRAINT fk_inventories_product
         FOREIGN KEY (product_id)
             REFERENCES products (id),

     CONSTRAINT chk_inventories_quantity
         CHECK (quantity >= 0),

     CONSTRAINT chk_inventories_reserved_quantity
         CHECK (
             reserved_quantity >= 0
                 AND reserved_quantity <= quantity
             )
);