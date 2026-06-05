-- V9__payment.sql — payment module tables
-- Owns: payment, payment_transaction
-- Cross-ref: payment.order_id → orders(id) (defined in V4__order.sql)

CREATE TABLE payment (
    id                  UUID         PRIMARY KEY,
    order_id            UUID         NOT NULL REFERENCES orders(id),
    method              VARCHAR(16)  NOT NULL,                              -- COD | VNPAY
    amount              NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    status              VARCHAR(16)  NOT NULL,                              -- PENDING | SUCCESS | FAILED | REFUNDED
    vnp_txn_ref         VARCHAR(64)  UNIQUE,                                -- "{orderCode}-{epochMs}"
    vnp_transaction_no  VARCHAR(64),
    vnp_response_code   VARCHAR(16),                                        -- "00", "07", "SUPERSEDED", "EXPIRED" ...
    paid_at             TIMESTAMPTZ,
    version             INT          NOT NULL DEFAULT 0,                    -- @Version optimistic lock
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_order               ON payment(order_id);
CREATE INDEX idx_payment_status_created      ON payment(status, created_at);

CREATE TABLE payment_transaction (
    id          BIGSERIAL    PRIMARY KEY,
    payment_id  UUID         NOT NULL REFERENCES payment(id) ON DELETE CASCADE,
    event_type  VARCHAR(16)  NOT NULL,                                      -- CREATE | IPN | RETURN
    raw_payload JSONB        NOT NULL,
    recorded_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_tx_payment ON payment_transaction(payment_id, recorded_at);
