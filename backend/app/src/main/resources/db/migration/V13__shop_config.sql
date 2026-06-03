-- V13__shop_config.sql — Singleton shop configuration row.
-- Stores admin-editable brand + pickup + fee fields so the app can serve any
-- shop (not just a single hardcoded one). Always exactly one row (id = 1).
-- Public miniapp/zaloapp read the brand fields; admin Settings page can edit.

CREATE TABLE shop_config (
    id              SMALLINT PRIMARY KEY CHECK (id = 1),
    name            VARCHAR(128) NOT NULL DEFAULT 'Shop Giao Hàng',
    tagline         VARCHAR(256) NOT NULL DEFAULT 'Giao đồ ăn nhanh • Thanh toán dễ',
    logo_url        TEXT,
    brand_primary   VARCHAR(7)   NOT NULL DEFAULT '#D97706',
    brand_secondary VARCHAR(7)   NOT NULL DEFAULT '#FB923C',
    contact_phone   VARCHAR(32),
    contact_email   VARCHAR(128),
    opening_hours   VARCHAR(64)  DEFAULT '08:00 - 22:00 hằng ngày',

    pickup_lat      NUMERIC(10, 7) NOT NULL DEFAULT 21.0285,
    pickup_lng      NUMERIC(10, 7) NOT NULL DEFAULT 105.8542,
    pickup_address  VARCHAR(256)   NOT NULL DEFAULT 'Shop default',

    fee_base        NUMERIC(12, 2) NOT NULL DEFAULT 15000,
    fee_per_km      NUMERIC(12, 2) NOT NULL DEFAULT 5000,
    free_km         NUMERIC(8, 3)  NOT NULL DEFAULT 0,

    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed the singleton row. Defaults match the YAML-based ShopConfigProperties
-- so behavior is identical on first boot until admin customizes via Settings.
INSERT INTO shop_config (id) VALUES (1) ON CONFLICT (id) DO NOTHING;
