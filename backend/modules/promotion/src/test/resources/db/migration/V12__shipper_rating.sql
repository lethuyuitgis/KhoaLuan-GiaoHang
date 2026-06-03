-- V12__shipper_rating.sql — shipper rates customer after DELIVERED (Should #6)
-- Owns: shipper_rating, telegram_user.customer_rating_avg/count
-- Cross-ref:
--   shipper_rating.order_id    → orders(id)         (V4__order.sql)
--   shipper_rating.shipper_id  → telegram_user(id)  (V2__auth.sql)
--   shipper_rating.customer_id → telegram_user(id)  (V2__auth.sql)
--
-- Counterpart to V10__rating (customer-rates-shipper). Kept as a separate
-- table — rather than role-on-rating — so that:
--   * UNIQUE(order_id) still exists per side (one rating per direction per order)
--   * The two aggregates can be recomputed independently without joins
--   * customer-facing reads of `rating` continue to mean "what the customer said"

CREATE TABLE shipper_rating (
    id           BIGSERIAL    PRIMARY KEY,
    order_id     UUID         NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    shipper_id   BIGINT       NOT NULL REFERENCES telegram_user(id),
    customer_id  BIGINT       NOT NULL REFERENCES telegram_user(id),
    stars        SMALLINT     NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment      TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Index supports "rating history for a customer" (admin or future feature).
-- order_id UNIQUE auto-creates an index — no extra needed.
CREATE INDEX idx_shipper_rating_customer_created ON shipper_rating(customer_id, created_at DESC);

-- Aggregates on telegram_user (kept lean — no separate customer_profile table yet).
ALTER TABLE telegram_user
    ADD COLUMN customer_rating_avg   NUMERIC(3,2) NOT NULL DEFAULT 0.00;
ALTER TABLE telegram_user
    ADD COLUMN customer_rating_count INT          NOT NULL DEFAULT 0;
