-- V14__voucher.sql — Code-based voucher / promo system.
--
-- Two voucher targets (SHIPPING reduces delivery_fee, PRODUCTS reduces subtotal),
-- two discount types (FIXED amount or PERCENT with cap). Each order may stack at
-- most one of each target. Validation rules (validity dates, min_order_amount,
-- max_uses_total, max_uses_per_customer) live in the application layer; the
-- database only enforces structural invariants via CHECK constraints.

CREATE TABLE voucher (
    id                     BIGSERIAL    PRIMARY KEY,
    code                   VARCHAR(32)  NOT NULL UNIQUE,
    name                   VARCHAR(128) NOT NULL,
    target                 VARCHAR(16)  NOT NULL
        CHECK (target IN ('SHIPPING', 'PRODUCTS')),
    discount_type          VARCHAR(16)  NOT NULL
        CHECK (discount_type IN ('FIXED', 'PERCENT')),
    discount_value         NUMERIC(12, 2) NOT NULL CHECK (discount_value > 0),
    max_discount           NUMERIC(12, 2)
        CHECK (max_discount IS NULL OR max_discount > 0),
    min_order_amount       NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (min_order_amount >= 0),
    valid_from             TIMESTAMPTZ NOT NULL,
    valid_until            TIMESTAMPTZ NOT NULL,
    max_uses_total         INT
        CHECK (max_uses_total IS NULL OR max_uses_total > 0),
    max_uses_per_customer  INT NOT NULL DEFAULT 1
        CHECK (max_uses_per_customer > 0),
    used_count             INT NOT NULL DEFAULT 0,
    is_active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (valid_until > valid_from),
    CHECK (discount_type = 'PERCENT' OR max_discount IS NULL)
);

-- (no separate CREATE INDEX statements — UNIQUE on `code` provides the only lookup index we need)

CREATE TABLE voucher_redemption (
    id                BIGSERIAL    PRIMARY KEY,
    voucher_id        BIGINT       NOT NULL REFERENCES voucher(id) ON DELETE RESTRICT,
    order_id          UUID         NOT NULL REFERENCES orders(id),
    customer_id       BIGINT       NOT NULL REFERENCES telegram_user(id),
    discount_applied  NUMERIC(12, 2) NOT NULL CHECK (discount_applied >= 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (voucher_id, order_id)
);

CREATE INDEX idx_redemption_customer ON voucher_redemption(customer_id, voucher_id);

-- Add discount columns + original-fee snapshot to orders.
ALTER TABLE orders
    ADD COLUMN discount_products      NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (discount_products >= 0),
    ADD COLUMN discount_shipping      NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (discount_shipping >= 0),
    ADD COLUMN delivery_fee_original  NUMERIC(12, 2);

UPDATE orders SET delivery_fee_original = delivery_fee WHERE delivery_fee_original IS NULL;

ALTER TABLE orders
    ALTER COLUMN delivery_fee_original SET NOT NULL,
    ADD CONSTRAINT chk_delivery_fee_original_nonneg
        CHECK (delivery_fee_original >= 0);

-- Seed two demo vouchers so reviewer can demo Checkout flow immediately.
INSERT INTO voucher
    (code, name, target, discount_type, discount_value, max_discount,
     min_order_amount, valid_from, valid_until, max_uses_total, max_uses_per_customer)
VALUES
    ('FREESHIP', 'Miễn phí ship 30k', 'SHIPPING', 'FIXED', 30000, NULL,
     0,      NOW() - INTERVAL '1 day', NOW() + INTERVAL '90 days', 100, 1),
    ('GIAM20K', 'Giảm 20 000đ cho đơn từ 100k', 'PRODUCTS', 'FIXED', 20000, NULL,
     100000, NOW() - INTERVAL '1 day', NOW() + INTERVAL '90 days', 100, 1)
ON CONFLICT (code) DO NOTHING;
