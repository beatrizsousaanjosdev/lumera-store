CREATE TABLE payments (
      id UUID PRIMARY KEY,
      order_id UUID NOT NULL,
      amount NUMERIC(19, 2) NOT NULL,
      created_at TIMESTAMPTZ NOT NULL,
      status VARCHAR(20) NOT NULL,

      CONSTRAINT uq_payments_order
          UNIQUE (order_id),

      CONSTRAINT chk_payments_amount
          CHECK (amount > 0),

      CONSTRAINT chk_payments_status
          CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED'))
);