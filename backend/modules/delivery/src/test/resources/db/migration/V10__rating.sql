-- V10__rating.sql — customer rating of shipper after DELIVERED (Should #10, P8)
-- Owns: rating
-- Cross-ref:
--   rating.order_id    → orders(id)         (V4__order.sql)
--   rating.customer_id → telegram_user(id)  (V2__auth.sql)
--   rating.shipper_id  → telegram_user(id)  (V2__auth.sql)

CREATE TABLE rating (
    id           BIGSERIAL    PRIMARY KEY,
    order_id     UUID         NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    customer_id  BIGINT       NOT NULL REFERENCES telegram_user(id),
    shipper_id   BIGINT       NOT NULL REFERENCES telegram_user(id),
    stars        SMALLINT     NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment      TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Index for "rating history per shipper" (admin shipper detail page, top-shippers report).
CREATE INDEX idx_rating_shipper_created ON rating(shipper_id, created_at DESC);
-- order_id UNIQUE auto-creates an index — no extra needed.
-- customer_id is read rarely (only "did this customer rate?") — skip the index.
