-- V16__saved_address.sql — Địa chỉ giao hàng thường dùng của khách.
--
-- Owns: saved_address
-- Used by: order (auto-lưu khi đặt đơn qua OrderCreatedEvent listener; API
--          /api/addresses cho Mini App autocomplete).
--
-- Tự động lưu: mỗi đơn thành công upsert 1 dòng theo (customer_id, lat, lng).
-- Trùng toạ độ → bump use_count + last_used_at thay vì tạo dòng mới.

CREATE TABLE saved_address (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    address         TEXT NOT NULL,
    lat             NUMERIC(10, 7) NOT NULL,
    lng             NUMERIC(10, 7) NOT NULL,
    use_count       INT NOT NULL DEFAULT 1,
    last_used_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_saved_address_coords UNIQUE (customer_id, lat, lng)
);

CREATE INDEX idx_saved_address_customer ON saved_address(customer_id, last_used_at DESC);
