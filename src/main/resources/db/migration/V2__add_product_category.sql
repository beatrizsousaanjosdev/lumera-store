ALTER TABLE products
    ADD COLUMN category VARCHAR(30);

UPDATE products
SET category = 'OTHER'
WHERE category IS NULL;

ALTER TABLE products
    ALTER COLUMN category SET NOT NULL;

ALTER TABLE products
    ADD CONSTRAINT chk_products_category
        CHECK (
            category IN (
                         'PERIPHERALS',
                         'COMPUTERS',
                         'MONITORS',
                         'ACCESSORIES',
                         'OTHER'
                )
            );