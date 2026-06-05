-- V6__delivery.sql — delivery module tables (assignment + shipper profile)
-- NOTE: location_ping table will be added in V7 (P6 Live Location)

CREATE TABLE shipper_profile (
    user_id             BIGINT PRIMARY KEY REFERENCES telegram_user(id) ON DELETE CASCADE,
    vehicle_type        VARCHAR(16) NOT NULL,
    license_plate       VARCHAR(16),
    current_state       VARCHAR(16) NOT NULL DEFAULT 'OFFLINE',
    rating_avg          NUMERIC(3, 2) NOT NULL DEFAULT 0.00,
    rating_count        INT NOT NULL DEFAULT 0,
    total_deliveries    INT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shipper_state ON shipper_profile(current_state);

CREATE TABLE delivery_assignment (
    id              UUID PRIMARY KEY,
    order_id        UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    shipper_id      BIGINT REFERENCES telegram_user(id),
    status          VARCHAR(16) NOT NULL,
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at     TIMESTAMPTZ,
    rejected_at     TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ
);

CREATE INDEX idx_delivery_assignment_shipper ON delivery_assignment(shipper_id, status);
CREATE INDEX idx_delivery_assignment_order ON delivery_assignment(order_id);
