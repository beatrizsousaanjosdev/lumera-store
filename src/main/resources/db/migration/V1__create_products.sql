CREATE TABLE products (
                          id UUID PRIMARY KEY,
                          name VARCHAR(150) NOT NULL,
                          description TEXT,
                          price NUMERIC(19, 2) NOT NULL,
                          active BOOLEAN NOT NULL,

                          CONSTRAINT chk_products_name_not_blank
                              CHECK (CHAR_LENGTH(TRIM(name)) > 0),

                          CONSTRAINT chk_products_price_positive
                              CHECK (price > 0)
);