CREATE TABLE order_payment_results (
    order_id UUID PRIMARY KEY REFERENCES orders(id),
    payment_id UUID NOT NULL UNIQUE,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    approved BOOLEAN NOT NULL
);
