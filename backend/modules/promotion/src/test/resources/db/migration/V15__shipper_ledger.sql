-- V15__shipper_ledger.sql — Shipper commission + earnings ledger.
--
-- Commission rate stored in shop_config (singleton). Per-order commission
-- snapshot in orders.shipper_commission. Append-only ledger table records
-- COMMISSION (+) and COD_OWED (-) auto entries on DELIVERED, plus manual
-- SETTLEMENT_PAYOUT (-)/SETTLEMENT_DEPOSIT (+) entries from admin.
-- Balance = SUM(amount) WHERE shipper_id = ? (positive = shop owes shipper).

ALTER TABLE shop_config
    ADD COLUMN shipper_commission_pct NUMERIC(5, 2) NOT NULL DEFAULT 80.00
        CHECK (shipper_commission_pct >= 0 AND shipper_commission_pct <= 100);

ALTER TABLE orders
    ADD COLUMN shipper_commission NUMERIC(12, 2)
        CHECK (shipper_commission IS NULL OR shipper_commission >= 0);

CREATE TABLE shipper_ledger (
    id          BIGSERIAL PRIMARY KEY,
    shipper_id  BIGINT NOT NULL REFERENCES shipper_profile(user_id) ON DELETE RESTRICT,
    entry_type  VARCHAR(32) NOT NULL CHECK (entry_type IN (
        'COMMISSION', 'COD_OWED', 'SETTLEMENT_PAYOUT', 'SETTLEMENT_DEPOSIT'
    )),
    amount      NUMERIC(12, 2) NOT NULL,
    order_id    UUID REFERENCES orders(id) ON DELETE RESTRICT,
    note        TEXT,
    created_by  VARCHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_shipper_date ON shipper_ledger(shipper_id, created_at DESC);
CREATE INDEX idx_ledger_order        ON shipper_ledger(order_id) WHERE order_id IS NOT NULL;
